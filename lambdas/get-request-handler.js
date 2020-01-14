const crypto = require('crypto');
const S3 = require('aws-sdk/clients/s3');
const {
  response,
  partsForKey,
  getUserFromEvent,
  isUserAuthorizedToReadFolder
} = require('./common');

const s3 = new S3({ apiVersion: '2019-09-21' });
const bucket = process.env.BUCKET_NAME;
const ONE_HOUR_IN_SECONDS = 60 * 60;

exports.handler = async function handler(event, context) {
  const { key: encodedKey } = event.pathParameters || {};
  const { uid } = getUserFromEvent(event);

  // decode once for path param, once for s3
  const key = decodeURIComponent(decodeURIComponent(encodedKey));

  const folder = (key.startsWith('/') ? key.slice(1) : key).split('/').slice(0, -4).join('/'); // y/m/d/id

  if ( !isUserAuthorizedToReadFolder(uid, folder) ) {
    return response(401, {}, JSON.stringify({ error: 'Unauthorized' }));
  }

  const dataUrl = await getSignedUrl('getObject', { Bucket: bucket, Key: key, Expires: ONE_HOUR_IN_SECONDS });

  const item = {
    ...partsForKey(key),
    dataUrl
  };

  return response(200, { 'Cache-Control': `max-age=600` }, JSON.stringify(item));
};

async function getSignedUrl(method, params) {
  return new Promise((resolve, reject) => {
    s3.getSignedUrl(method, params, (err, url) => {
      if ( err ) {
        return reject(err);
      }

      resolve(url);
    });
  });
}
