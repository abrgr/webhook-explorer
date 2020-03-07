const S3 = require('aws-sdk/clients/s3');
const {
  response,
  partsForKey,
  getAuditKey,
  getUserFromEvent,
  isUserAuthorizedToReadFolder,
  fingerprintTagAndDateToUserVisibleTags,
  auditKeyToFingerprintTagAndDate
} = require('./common');

const s3 = new S3({ apiVersion: '2019-09-21' });
const bucket = process.env.BUCKET_NAME;
const ONE_HOUR_IN_SECONDS = 60 * 60;

exports.handler = async function handler(event) {
  const { key: encodedKey } = event.pathParameters || {};
  const { uid } = getUserFromEvent(event);

  // decode once for path param, once for s3
  const key = decodeURIComponent(decodeURIComponent(encodedKey));

  const folder = (key.startsWith('/') ? key.slice(1) : key).split('/').slice(0, -4).join('/'); // y/m/d/id

  if ( !isUserAuthorizedToReadFolder(uid, folder) ) {
    return response(401, {}, JSON.stringify({ error: 'Unauthorized' }));
  }

  const parts = partsForKey(key);
  const { date, fingerprint } = parts;

  const dataUrl = await getSignedUrl('getObject', { Bucket: bucket, Key: key, Expires: ONE_HOUR_IN_SECONDS });

  const detailsObj = await s3.getObject({
    Bucket: bucket,
    Key: key
  }).promise();
  const details = JSON.parse(detailsObj.Body.toString('utf8'));

  const auditKey = getAuditKey(date, fingerprint, '');
  const rawTagKeys = await listAll({
    Bucket: bucket,
    Prefix: auditKey,
    Delimiter: auditKey.slice(-1)
  });
  const tagsByFingerprint = fingerprintTagAndDateToUserVisibleTags(uid, rawTagKeys.map(auditKeyToFingerprintTagAndDate));
  const tags = tagsByFingerprint[fingerprint];

  const item = {
    ...parts,
    dataUrl,
    details,
    tags
  };

  return response(200, { 'Cache-Control': `max-age=600` }, JSON.stringify(item));
};

async function listAll(params, prev) {
  const data = await s3.listObjectsV2(params).promise();
  const keys = data.Contents.map(c => c.Key);
  const next = (prev || []).concat(keys);
  if ( data.NextContinuationToken ) {
    return listAll({ ...params, ContinuationToken: data.NextContinuationToken }, next);
  }

  return next;
}

async function getSignedUrl(method, params) {
  return new Promise((resolve, reject) => {
    s3.getSignedUrl(method, params, (err, url) => {
      if ( err ) {
        reject(err);
        return;
      }

      resolve(url);
    });
  });
}
