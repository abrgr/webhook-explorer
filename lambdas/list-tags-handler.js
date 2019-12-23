const path = require('path');
const S3 = require('aws-sdk/clients/s3');
const {
  response,
  getUserFromEvent,
  folderForTag,
  getPrivateTag,
  isValidUserSpecifiedTag,
  isUserAuthorizedToReadFolder,
  isUserAuthorizedToWriteFolder
} = require('./common');

const s3 = new S3({ apiVersion: '2019-09-21' });
const bucket = process.env.BUCKET_NAME;

exports.handler = async function handler(event, context) {
  const { uid } = getUserFromEvent(event);

  // TODO: handle continuation token
  const { CommonPrefixes: userFolders } = await s3List(folderForTag(getPrivateTag(uid, '')));
  const { CommonPrefixes: allTopLevelFolders } = await s3List(folderForTag(''));
  const publicTags = allTopLevelFolders.map(finalPathPartForPrefix).filter(isValidUserSpecifiedTag);

  const result = {
    userTags: userFolders.map(finalPathPartForPrefix).filter(isValidUserSpecifiedTag),
    publicTags: {
      writable: publicTags.filter(t => isUserAuthorizedToWriteFolder(uid, folderForTag(t))),
      readable: publicTags.filter(t => isUserAuthorizedToReadFolder(uid, folderForTag(t)))
    }
  };

  return response(200, { 'Cache-Control': 'max-age=60' }, JSON.stringify(result));
};

function finalPathPartForPrefix(commonPrefix) {
  return commonPrefix.Prefix.replace(/[\/]$/, '').split('/').slice(-1)[0];
}

async function s3List(prefix, opts) {
  const params = {
    ...opts,
    Bucket: bucket,
    Delimiter: '/',
    Prefix: prefix
  };

  return await s3.listObjectsV2(params).promise();
}
