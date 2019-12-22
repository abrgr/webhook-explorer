const crypto = require('crypto');
const S3 = require('aws-sdk/clients/s3');
const { keyForParts, replaceKeyTag, folderForTag, response } = require('./common');

const s3 = new S3({ apiVersion: '2019-09-21' });
const bucket = process.env.BUCKET_NAME;

exports.handler = async function handler(event, context) {
  const isFav = event.queryStringParameters['fav'];
  const tag = isFav
            ? 'fav' // TODO: get user name
            : event.queryStringParameters['tag'];
  const req = JSON.parse(event.body);
  const {
    sourceKey,
    req: {
      host,
      protocol,
      path,
      qs,
      method,
      iso,
      req: {
        headers: reqHeaders,
        body: reqBody
      },
      res: {
        headers: resHeaders,
        body: resBody
      }
    }
  } = req;
  const msg = {
    host,
    protocol,
    path,
    qs,
    method,
    iso,
    req: {
      headers: reqHeaders,
      body: reqBody
    },
    res: {
      headers: resHeaders,
      body: resBody
    }
  };
  const key = sourceKey
            ? replaceKeyTag(tag, sourceKey)
            : keyForParts(folderForTag(tag), iso, method, host, path, crypto.randomBytes(16).toString('hex'));

  await s3.putObject({
    Body: JSON.stringify(msg),
    Bucket: bucket,
    Key: key,
    ContentType: 'application/json'
  }).promise();

  return response(200, {}, JSON.stringify({ success: true }));
};
