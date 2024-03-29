const S3 = require('aws-sdk/clients/s3');
const {
  response,
  partsForKey,
  folderForTag,
  getPrivateTag,
  getUserFromEvent,
  getTagForFavorite,
  isUserAuthorizedToReadFolder
} = require('./common');
const { getNextListing } = require('./ymd-lister');

const s3 = new S3({ apiVersion: '2019-09-21' });
const bucket = process.env.BUCKET_NAME;
const ONE_HOUR_IN_SECONDS = 60 * 60;

exports.handler = async function handler(event) {
  const { folder, fav, pub, tag, ymd, token } =
    event.queryStringParameters || {};
  const { uid } = getUserFromEvent(event);
  const fullTag = pub ? tag : tag ? getPrivateTag(uid, tag) : null;
  const resolvedTag = fav ? getTagForFavorite(uid) : fullTag;
  const resolvedFolder = resolvedTag
    ? folderForTag(resolvedTag)
    : folder || 'all';

  if (!isUserAuthorizedToReadFolder(uid, resolvedFolder)) {
    return response(401, {}, JSON.stringify({ error: 'Unauthorized' }));
  }

  const page = await getNextListing(resolvedFolder, ymd, token, makeItem);
  const cacheSeconds = ymd || token ? 300 : 5;

  return response(
    200,
    { 'Cache-Control': `max-age=${cacheSeconds}` },
    JSON.stringify(page)
  );
};

async function makeItem(key) {
  const parts = partsForKey(key);
  return {
    ...parts,
    dataUrl: await getSignedUrl('getObject', {
      Bucket: bucket,
      Key: key,
      Expires: ONE_HOUR_IN_SECONDS
    })
  };
}

async function getSignedUrl(method, params) {
  return new Promise((resolve, reject) => {
    s3.getSignedUrl(method, params, (err, url) => {
      if (err) {
        reject(err);
        return;
      }

      resolve(url);
    });
  });
}
