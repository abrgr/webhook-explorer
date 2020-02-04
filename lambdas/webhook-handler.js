const { keyForParts, response, hashMsg, parseRequestCookies } = require('./common');
const S3 = require('aws-sdk/clients/s3');
const DynamoDB = require('aws-sdk/clients/dynamodb');
const Busboy = require('busboy');

const s3 = new S3({ apiVersion: '2019-09-21' });
const documentClient = new DynamoDB.DocumentClient({ apiVersion: '2019-09-21' });

const bucket = process.env.BUCKET_NAME;
const table = process.env.HANDLERS_TABLE_NAME;

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
  const status = 200;
  const form = await parseForm(event);

  const protoMethod = `https:${method}`;
  const handlerKey = await findHandlerKey(protoMethod, host, path.slice(1).split('/'));
  console.log(JSON.stringify({
    msg: 'found handler',
    handlerKey
  }));

  const msg = {
    host,
    protocol,
    path,
    qs,
    method,
    iso,
    status,
    req: {
      headers,
      body
    },
    res: {
      headers: {},
      body: "OK"
    }
  };
  msg.fingerprint = hashMsg(msg);
  msg.req.form = form;
  msg.req.cookies = parseRequestCookies(headers.Cookie || headers.cookie);
  msg.res.cookies = null;
  const key = keyForParts('all', iso, method, host, path, status, msg.fingerprint);
  await s3.putObject({
    Body: JSON.stringify(msg),
    Bucket: bucket,
    Key: key,
    ContentType: 'application/json'
  }).promise();

  return response(status, {}, "OK");
};

async function findHandlerKey(protoMethod, domainPathPrefix, restPathParts) {
  console.log('start findHandlerKey', {protoMethod, domainPathPrefix, restPathParts});

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
  const { Body } = await s3.getObject({
    Bucket: bucket,
    Key: key
  }).promise();

  return Body;
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
