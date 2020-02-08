const { keyForParts, response, hashMsg, parseRequestCookies } = require('./common');
const http = require('http');
const https = require('https');
const url = require('url');
const zlib = require('zlib');
const S3 = require('aws-sdk/clients/s3');
const DynamoDB = require('aws-sdk/clients/dynamodb');
const Busboy = require('busboy');

const s3 = new S3({ apiVersion: '2019-09-21' });
const documentClient = new DynamoDB.DocumentClient({ apiVersion: '2019-09-21' });

const bucket = process.env.BUCKET_NAME;
const table = process.env.HANDLERS_TABLE_NAME;

const handlers = {
  mock: handleMock,
  proxy: handleProxy
};

exports.handler = async function handler(event, context) {
  const now = new Date();
  const iso = now.toISOString();
  const method = event.httpMethod;
  const path = event.path;
  const headers = event.headers || {};
  const host = headers.Host || headers.host;
  const body = event.body;
  const protocol = (headers['X-Forwarded-Proto'] || headers['x-forwarded-proto'] || '').toLowerCase();
  const qs = event.queryStringParameters || {};
  const form = await parseForm(event);

  const protoMethod = `https:${method}`;
  const handlerKey = await findHandlerKey(protoMethod, host, path.slice(1).split('/'));
  if ( !handlerKey ) {
    console.log('No matching route', { protoMethod, host, path });
    return response(502, { error: "No matching Rogo route" }, "No matching Rogo route");
  }

  const handlerDef = await loadHandlerData(handlerKey);
  if ( !handlerDef ) {
    console.error('No handler found for key', { protoMethod, host, path, handlerKey });
    return response(502, { error: "No matching Rogo route" }, "No matching Rogo route");
  }

  console.log(JSON.stringify({msg: "handlerDef", handlerDef}));

  const captures = getCaptures(handlerDef, event, form);
  console.log(JSON.stringify({msg: 'captures', captures}));
  const matcher = (handlerDef.matchers || []).find(doesMatch.bind(null, captures));
  if ( !matcher ) {
    console.error('No Rogo matcher satisfied', { captures, handlerDef });
    return response(502, { error: "No Rogo matcher satisfied" }, "No Rogo matcher satisfied");
  }

  const { handler } = matcher;
  const handlerFn = handlers[handler.type];
  if ( !handlerFn ) {
    console.error('Unknown handler type', { handler, path, protoMethod });
    return response(502, { error: 'Unknown Rogo handler type' }, 'Unknown Rogo handler type');
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
    msg.fingerprint = hashMsg(msg);
    msg.req.form = form;
    msg.req.cookies = parseRequestCookies(headers.Cookie || headers.cookie);
    msg.res.cookies = null;
    const key = keyForParts('all', iso, method, host, path, result.status, msg.fingerprint);
    await s3.putObject({
      Body: JSON.stringify(msg),
      Bucket: bucket,
      Key: key,
      ContentType: 'application/json'
    }).promise();

    return response(result.status, result.headers, result.body, result.isBase64Encoded);
  } catch ( err ) {
    console.error(err);

    const msg = {
      ...baseMsg,
      status: 0,
      err
    };
    // TODO: write msg

    return response(502, { error: 'Error processing request' }, 'Error processing request');
  }

};

async function handleMock(captures, { mock: { res: mockRes } }) {
  const headers = Object.keys(mockRes.headers)
    .reduce((h, headerKey) => ({
      ...h,
      [headerKey]: fillTemplate(captures, mockRes.headers[headerKey])
    }), {});
  return {
    headers,
    status: mockRes.status,
    body: fillTemplate(captures, mockRes.body)
  };
}

async function handleProxy(captures, { proxy: { remoteUrl } }, { body, isBase64Encoded, headers, httpMethod }) {
  return new Promise((resolve, reject) => {
    const proxyUrl = fillTemplate(captures, remoteUrl);
    const parsedUrl = url.parse(proxyUrl);
    const isHttps = (parsedUrl.protocol || 'https').indexOf('https') >= 0;
    const opts = {
      auth: parsedUrl.auth || void 0,
      hostname: parsedUrl.hostname,
      port: parsedUrl.port || void 0,
      path: parsedUrl.path,
      method: httpMethod,
      headers: Object.keys(headers)
                     .filter(h => h.toLowerCase() !== 'host')
                     .reduce((hs, h) => ({
                        ...hs,
                        [h]: headers[h]
                      }, {}))
    };

    console.log(JSON.stringify({...opts, msg: "OPTS"}));
    const protoHandler = isHttps ? https : http;
    const req = protoHandler.request(opts, res => {
      let respBody = Buffer.from([]);
      res.on('data', chunk => {
        respBody = Buffer.concat([respBody, chunk]);
      });
      res.on('end', () => {
        const enc = res.headers['content-encoding'];
        const ct = res.headers['content-type'];
        const decompressedRespBody = decompress(enc, respBody);
        const isUtf = !!/^text|^multipart|[\/](javascript|json|edn|xml|xhtml)/.exec(ct);
        return resolve({
          status: res.status,
          headers: res.headers,
          body: decompressedRespBody.toString(isUtf ? 'utf8' : 'base64'),
          isBase64Encoded: !isUtf
        });
      });
    });
    req.on('error', reject);

    req.write(body, isBase64Encoded ? 'base64' : 'utf8');
    req.end();
  });
}

function decompress(encoding, body) {
  switch ( encoding ) {
    case 'br':
      return zlib.brotliDecompressSync(body);
    case 'gzip':
      return zlib.gunzipSync(body);
    case 'deflate':
      return zlib.deflateSync(body);
    default:
      return body;
  }
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

MissingTemplateVarError.name = "MissingTemplateVarError";

function fillTemplate(captures, template) {
  return template.replace(/\${([^}]+)}/g, (_, c) => {
    if ( !captures.hasOwnProperty(c) ) {
      throw new MissingTemplateVarError(c);
    }
    return captures[c];
  });
}

function doesMatch(captures, { matches }) {
  const capsToMatch = Object.keys(matches || {});
  return capsToMatch.every(cap => captures[cap] === matches[cap]);
}

function getCaptures({ path: handlerPath }, { path }, form) {
  const pathCaptures = getPathCaptures(handlerPath, path);
  return pathCaptures;
}

function getPathCaptures(pathCaptures, path) {
  const capParts = pathCaptures.split('/');
  const pathParts = path.split('/');
  const len = Math.min(capParts.length, pathParts.length);

  const caps = {};
  for ( let i = 0; i < len; ++i ) {
    const [, capName] = /[{]([^}]+)[}]/.exec(capParts[i]) || [];
    const capVal = pathParts[i];
    if ( capName ) {
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

  const literalResult = await maybeHandleNode(literalDomainPath, protoMethod, isLast, nextPathParts);
  if ( typeof literalResult !== 'undefined' ) {
    return literalResult;
  }

  const paramResult = await maybeHandleNode(paramDomainPath, protoMethod, isLast, nextPathParts);

  return paramResult || null;
}

async function maybeHandleNode(domainPath, protoMethod, isLast, nextPathParts) {
  const node = await loadHandler(domainPath, protoMethod);
  if ( !node ) {
    return;
  }

  if ( isLast ) {
    if ( node.exactKey ) {
      return node.exactKey;
    }
  } else if ( node.exactSuffixCount || node.prefixSuffixCount ) {
    const nextResult = await findHandlerKey(protoMethod, domainPath, nextPathParts);
    if ( nextResult ) {
      return nextResult;
    }
  }
  
  if ( node.prefixKey && !isLast ) {
    return node.prefixKey;
  }
}

async function loadHandler(domainPath, protoMethod) {
  const { Item } = await documentClient.get({
    TableName: table,
    Key: {
      domainPath,
      protoMethod
    }
  }).promise();

  return Item;
}

async function loadHandlerData(key) {
  console.log(JSON.stringify({msg: "loadHandlerData", bucket, key}));
  const { Body } = await s3.getObject({
    Bucket: bucket,
    Key: key
  }).promise();

  return JSON.parse(Body);
}

async function parseForm(event) {
  return new Promise((resolve, reject) => {
    let busboy;
    try {
      busboy = new Busboy({
        headers: {
          'content-type': event.headers['Content-Type'] || event.headers['content-type']
        }
      });
    } catch ( e ) {
      if ( e.message.startsWith('Unsupported content type')
          || e.message === 'Missing Content-Type' ) {
        return resolve(null);
      }
      return reject(e);
    }

    const result = {
      files: [],
      fields: {}
    };

    busboy.on('file', (fieldname, file, filename, encoding, mimetype) => {
      let fileData = null;
      file.on('data', (data) => {
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
  });
}
