const {
  keyForParts,
  response,
  hashMsg,
  parseRequestCookies,
  executeRequest
} = require('./common');
const S3 = require('aws-sdk/clients/s3');
const DynamoDB = require('aws-sdk/clients/dynamodb');
const Busboy = require('busboy');
const jp = require('jsonpath');

const s3 = new S3({ apiVersion: '2019-09-21' });
const documentClient = new DynamoDB.DocumentClient({
  apiVersion: '2019-09-21'
});

const bucket = process.env.BUCKET_NAME;
const table = process.env.HANDLERS_TABLE_NAME;

const handlers = {
  mock: handleMock,
  proxy: handleProxy
};

exports.handler = async function handler(event) {
  const startTime = Date.now();
  const now = new Date();
  const iso = now.toISOString();
  const method = event.httpMethod;
  const path = event.path;
  const headers = event.headers || {};
  const host = headers.Host || headers.host;
  const body = event.body;
  const protocol = (
    headers['X-Forwarded-Proto'] ||
    headers['x-forwarded-proto'] ||
    ''
  ).toLowerCase();
  const qs = event.queryStringParameters || {};
  const form = await parseForm(event);
  const cookies = parseRequestCookies(headers.Cookie || headers.cookie);

  const protoMethod = `https:${method.toLowerCase()}`;
  const matchedHandler =
    (await findHandlerKey(protoMethod, host, path.slice(1).split('/'))) || {};
  const { key: handlerKey } = matchedHandler;
  if (!handlerKey) {
    console.log('No matching route', { protoMethod, host, path });
    return response(
      502,
      { 'x-rogo-error': 'No matching Rogo route' },
      'No matching Rogo route'
    );
  }

  const handlerDef = await loadHandlerData(handlerKey);
  if (!handlerDef) {
    console.error('No handler found for key', {
      protoMethod,
      host,
      path,
      handlerKey
    });
    return response(
      502,
      { 'x-rogo-error': 'No matching Rogo route' },
      'No matching Rogo route'
    );
  }

  const captures = getCaptures(handlerDef, event, form);
  const matcher = (handlerDef.matchers || []).find(
    doesMatch.bind(null, captures)
  );
  if (!matcher) {
    console.error('No Rogo matcher satisfied', { captures, handlerDef });
    return response(
      502,
      { 'x-rogo-error': 'No Rogo matcher satisfied' },
      'No Rogo matcher satisfied'
    );
  }

  const matchEndTime = Date.now();

  const { handler } = matcher;
  const handlerFn = handlers[handler.type];
  if (!handlerFn) {
    console.error('Unknown handler type', { handler, path, protoMethod });
    return response(
      502,
      { 'x-rogo-error': 'Unknown Rogo handler type' },
      'Unknown Rogo handler type'
    );
  }

  const baseMsg = {
    host,
    protocol,
    path,
    qs,
    method,
    iso,
    req: {
      headers,
      body
    }
  };

  try {
    const result = await handlerFn(captures, handler, event);

    const msg = {
      ...baseMsg,
      status: result.status,
      res: {
        headers: result.headers,
        body: result.body
      }
    };

    const enhancedMsg = await writeMsg(
      msg,
      startTime,
      matchEndTime,
      form,
      cookies,
      matchedHandler
    );

    return response(
      enhancedMsg.status,
      result.headers,
      result.body,
      result.isBase64Encoded
    );
  } catch (processingError) {
    console.error('Error processing handler', processingError);

    const msg = {
      ...baseMsg,
      status: 0,
      processingError
    };

    await writeMsg(msg, startTime, matchEndTime, form, cookies, matchedHandler);

    return response(
      502,
      { error: 'Error processing request' },
      'Error processing request'
    );
  }
};

async function writeMsg(
  msg,
  startTime,
  matchEndTime,
  reqForm,
  reqCookies,
  matchedHandler
) {
  const enhancedMsg = {
    ...msg,
    matchedHandler,
    timing: {
      matchTimeMs: matchEndTime - startTime,
      processingTimeMs: Date.now() - matchEndTime
    },
    fingerprint: hashMsg(msg),
    req: {
      ...msg.req,
      form: reqForm,
      cookies: reqCookies
    }
  };
  const key = keyForParts(
    'all',
    msg.iso,
    msg.method,
    msg.host,
    msg.path,
    msg.status,
    enhancedMsg.fingerprint
  );
  await s3
    .putObject({
      Body: JSON.stringify(enhancedMsg),
      Bucket: bucket,
      Key: key,
      ContentType: 'application/json'
    })
    .promise();

  return enhancedMsg;
}

async function handleMock(captures, { mock: { res: mockRes } }) {
  const headers = Object.keys(mockRes.headers).reduce(
    (h, headerKey) => ({
      ...h,
      [headerKey]: fillTemplate(captures, mockRes.headers[headerKey])
    }),
    {}
  );
  return {
    headers,
    status: mockRes.status,
    body: fillTemplate(captures, mockRes.body)
  };
}

async function handleProxy(
  captures,
  { proxy: { remoteUrl } },
  { body, isBase64Encoded, headers, httpMethod }
) {
  const adjHeaders = Object.keys(headers)
    .filter(h => h.toLowerCase() !== 'host')
    .reduce(
      (hs, h) => ({
        ...hs,
        [h]: headers[h]
      }),
      {}
    );
  const proxyUrl = fillTemplate(captures, remoteUrl);
  const bodyBuffer =
    body && Buffer.from(body, isBase64Encoded ? 'base64' : 'utf8');

  return executeRequest(httpMethod, proxyUrl, adjHeaders, bodyBuffer);
}

class MissingTemplateVarError extends Error {
  constructor(variable) {
    super(`Missing template variable: ${variable}`);
    this.name = MissingTemplateVarError.name;
  }

  static is(err) {
    return err.name === MissingTemplateVarError.name;
  }
}

MissingTemplateVarError.name = 'MissingTemplateVarError';

function fillTemplate(captures, template) {
  return template.replace(/\${([^}]+)}/g, (_, c) => {
    if (!captures.hasOwnProperty(c)) {
      throw new MissingTemplateVarError(c);
    }
    return captures[c];
  });
}

function doesMatch(captures, { matches }) {
  const capsToMatch = Object.keys(matches || {});
  return capsToMatch.every(cap => captures[cap] === matches[cap]);
}

function getCaptures(
  { path: handlerPath, captures },
  { path, headers, body, isBase64Encoded },
  form
) {
  const { body: bodyCaptureSpec, headers: headerCaptureSpec } = captures || {};
  const bodies = {
    form: form,
    raw: body ? Buffer.from(body, isBase64Encoded ? 'base64' : 'utf8') : null
  };
  const pathCaptures = getPathCaptures(handlerPath, path);
  const headerCaptures =
    headerCaptureSpec && !!headers
      ? getHeaderCaptures(headerCaptureSpec, headers)
      : {};
  const bodyCaptures =
    bodyCaptureSpec && !!body ? getBodyCaptures(bodyCaptureSpec, bodies) : {};
  return {
    ...pathCaptures,
    ...headerCaptures,
    ...bodyCaptures
  };
}

const bodyCaptureHandlers = {
  json: getJsonBodyCaptures,
  form: getFormBodyCaptures
};

function getBodyCaptures({ type, captures }, bodies) {
  const captureHandler = bodyCaptureHandlers[type];
  return captureHandler ? captureHandler(captures, bodies) : {};
}

function getJsonBodyCaptures(captures, { json: jsonBuffer }) {
  const json = jsonBuffer.toString('utf8');
  try {
    const obj = JSON.parse(json);
    return Object.keys(captures).reduce(
      (caps, jsonPath) => ({
        ...caps,
        [captures[jsonPath].templateVar]: jp.query(obj, jsonPath)
      }),
      {}
    );
  } catch (err) {
    console.error('Failed to parse JSON body');
    return {};
  }
}

function getFormBodyCaptures(captures, { form: { fields } }) {
  return Object.keys(captures).reduce(
    (field, caps) => ({
      ...caps,
      [captures[field].templateVar]: (fields || {})[field]
    }),
    {}
  );
}

function getHeaderCaptures(headerCaptures, headers) {
  return Object.keys(headerCaptures).reduce((caps, h) => {
    if (!headers.hasOwnProperty(h)) {
      return caps;
    }

    const hv = headers[h];
    const tv = headerCaptures[h].templateVar;
    return {
      ...caps,
      [tv]: hv
    };
  }, {});
}

function getPathCaptures(pathCaptures, path) {
  const capParts = pathCaptures.split('/');
  const pathParts = path.split('/');
  const len = Math.min(capParts.length, pathParts.length);

  const caps = {};
  for (let i = 0; i < len; ++i) {
    const [, capName] = /[{]([^}]+)[}]/.exec(capParts[i]) || [];
    const capVal = pathParts[i];
    if (capName) {
      caps[capName] = capVal;
    }
  }

  return caps;
}

async function findHandlerKey(protoMethod, domainPathPrefix, restPathParts) {
  const pathPart = restPathParts[0];
  const nextPathParts = restPathParts.slice(1);
  const literalDomainPath = domainPathPrefix + '/' + pathPart;
  const paramDomainPath = domainPathPrefix + '/{}';
  const isLast = !nextPathParts.length;

  const literalResult = await maybeHandleNode(
    literalDomainPath,
    protoMethod,
    isLast,
    nextPathParts
  );
  if (typeof literalResult !== 'undefined') {
    return literalResult;
  }

  const paramResult = await maybeHandleNode(
    paramDomainPath,
    protoMethod,
    isLast,
    nextPathParts
  );

  return paramResult || null;
}

async function maybeHandleNode(domainPath, protoMethod, isLast, nextPathParts) {
  const node = await loadHandler(domainPath, protoMethod);
  if (!node) {
    return void 0;
  }

  if (isLast) {
    if (node.exactKey) {
      return {
        key: node.exactKey,
        domainPath: node.domainPath,
        protoMethod: node.protoMethod,
        matchType: 'exact'
      };
    }
  } else if (node.exactSuffixCount || node.prefixSuffixCount) {
    const nextResult = await findHandlerKey(
      protoMethod,
      domainPath,
      nextPathParts
    );
    if (nextResult) {
      return nextResult;
    }
  }

  if (node.prefixKey && !isLast) {
    return {
      key: node.prefixKey,
      handlerPath: node.domainPath,
      protoMethod: node.protoMethod,
      matchType: 'prefix'
    };
  }

  return void 0;
}

async function loadHandler(domainPath, protoMethod) {
  const { Item } = await documentClient
    .get({
      TableName: table,
      Key: {
        domainPath,
        protoMethod
      }
    })
    .promise();

  return Item;
}

async function loadHandlerData(key) {
  const { Body } = await s3
    .getObject({
      Bucket: bucket,
      Key: key
    })
    .promise();

  return JSON.parse(Body);
}

async function parseForm(event) {
  return new Promise((resolve, reject) => {
    if (!event.headers) {
      resolve(null);
      return;
    }

    try {
      const busboy = new Busboy({
        headers: {
          'content-type':
            event.headers['Content-Type'] || event.headers['content-type']
        }
      });

      const result = {
        files: [],
        fields: {}
      };

      busboy.on('file', (fieldname, file, filename, encoding, mimetype) => {
        let fileData = null;
        file.on('data', data => {
          fileData = data.toString('base64');
        });

        file.on('end', () => {
          result.files.push({
            filename,
            mimetype,
            originalEncoding: encoding,
            dataEncoding: 'base64',
            data: fileData
          });
        });
      });

      busboy.on('field', (fieldname, value) => {
        result.fields[fieldname] = value;
      });

      busboy.on('error', reject);
      busboy.on('finish', () => {
        resolve(result);
      });

      busboy.write(event.body, event.isBase64Encoded ? 'base64' : 'binary');
      busboy.end();
    } catch (e) {
      if (
        e.message.startsWith('Unsupported content type') ||
        e.message === 'Missing Content-Type'
      ) {
        resolve(null);
        return;
      }
      reject(e);
    }
  });
}
