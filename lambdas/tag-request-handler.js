const crypto = require('crypto');
const S3 = require('aws-sdk/clients/s3');
const {
  keyForParts,
  folderForTag,
  response,
  getUserFromEvent,
  getTagForFavorite,
  getPrivateTag,
  isValidUserSpecifiedTag,
  isUserAuthorizedToWriteFolder
} = require('./common');

const s3 = new S3({ apiVersion: '2019-09-21' });
const bucket = process.env.BUCKET_NAME;

exports.handler = async function handler(event, context) {
  const { uid } = getUserFromEvent(event);
  const isFav = event.queryStringParameters['fav'];
  const isPublic = event.queryStringParameters['pub'];
  const userProvidedTag = event.queryStringParameters['tag'];
  const tag = isFav
            ? getTagForFavorite(uid)
            : (isPublic ? userProvidedTag : getPrivateTag(uid, userProvidedTag));

  if ( !isFav && !isValidUserSpecifiedTag(tag) ) {
    console.error('Invalid user-specified tag', { tag, event });
    return response(400, {}, { error: 'Invalid tag' });
  }

  const folder = folderForTag(tag);

  if ( !isUserAuthorizedToWriteFolder(uid, folder) ) {
    console.error('User unauthorized for folder', { uid, folder, event });
    return response(401, {}, JSON.stringify({ error: 'Unauthorized' }));
  }

  const req = JSON.parse(event.body);
  const {
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
  const key = keyForParts(folder, iso, method, host, path, crypto.randomBytes(16).toString('hex'));

  await s3.putObject({
    Body: JSON.stringify(msg),
    Bucket: bucket,
    Key: key,
    ContentType: 'application/json'
  }).promise();

  return response(200, {}, JSON.stringify({ success: true }));
};
