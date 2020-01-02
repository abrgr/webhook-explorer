const S3 = require('aws-sdk/clients/s3');
const { keyForParts, response, hashMsg } = require('./common');

const s3 = new S3({ apiVersion: '2019-09-21' });
const bucket = process.env.BUCKET_NAME;

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
  const key = keyForParts('all', iso, method, host, path, status, msg.fingerprint);
  await s3.putObject({
    Body: JSON.stringify(msg),
    Bucket: bucket,
    Key: key,
    ContentType: 'application/json'
  }).promise();

  return response(status, {}, "OK");
};
