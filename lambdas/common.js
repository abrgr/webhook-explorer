const crypto = require('crypto');
const EXPECTED_AUD = process.env.EXPECTED_AUD;

module.exports = {
  endOfToday,
  keyForParts,
  replaceKeyTag,
  folderForTag,
  response,
  getUserFromEvent,
  getTagForFavorite,
  getPrivateTag,
  isValidUserSpecifiedTag,
  isUserAuthorizedToReadFolder,
  isUserAuthorizedToWriteFolder,
  hashMsg,
  getAuditKey,
  auditKeyToFingerprintAndTag
};

function getUserFromEvent(event) {
  const {
    requestContext: {
      authorizer: {
        claims: {
          aud,
          "cognito:username": uid,
          email
        }
      }
    }
  } = event;

  if ( aud !== EXPECTED_AUD ) {
    console.error('Bad authorizer audience', { event });
    throw new Error('Unauthorized');
  }

  return {
    email,
    uid
  };
}

function isUserAuthorizedToReadFolder(uid, folder) {
  // TODO: || isAdmin(uid);
  return isUserAuthorizedToWriteFolder(uid, folder);
}

function isUserAuthorizedToWriteFolder(uid, folder) {
  // folders either look like "all", "tags/public-tag", or "tags/*uid*/private-tag"
  const folderParts = folder.split('/');
  if ( folderParts.length < 3 ) {
    return true;
  }

  const folderUid = folderParts[1];
  return folderUid === encodeUidForTagFolder(uid);
}


function endOfToday(todayEpoch) {
  const tomorrow = new Date(todayEpoch);
  tomorrow.setDate(tomorrow.getDate() + 1);
  tomorrow.setHours(0);
  tomorrow.setMinutes(0);
  tomorrow.setSeconds(0);
  tomorrow.setMilliseconds(0);
  return tomorrow.getTime();
}

function keyForParts(folder, iso, method, host, path, reqId) {
  const ymd = iso.split('T')[0];
  const date = new Date(iso);
  const epoch = date.getTime();
  const sort = ('' + (endOfToday(epoch) - epoch)).padStart(8, '0');
  return `${folder}/${ymd.replace(/-/g, '/')}/${sort}:${encodeURIComponent(iso)}:${encodeURIComponent(method)}:${encodeURIComponent(host)}:${encodeURIComponent(path)}:${encodeURIComponent(reqId)}`;
}

function folderForTag(tag) {
  return `tags/${tag}`;
}

function replaceKeyTag(newTag, sourceKey) {
  const [id, sort, d, m, y] = sourceKey.split('/').reverse();
  const key = `${folderForTag(newTag)}/${y}/${m}/${d}/${sort}/${id}`;
  return key;
}

function getTagForFavorite(uid) {
  return getPrivateTag(uid, '*fav*');
}

function getPrivateTag(uid, tag) {
  return `${encodeUidForTagFolder(uid)}/${tag}`;
}

function encodeUidForTagFolder(uid) {
  return `*${encodeURIComponent(uid)}*`;
}

function isValidUserSpecifiedTag(tag) {
  return !/[*\/]/.exec(tag);
}

function response(statusCode, headers, body) {
  return {
    isBase64Encoded: false,
    statusCode,
    headers: {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Headers': 'Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token',
      'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
      ...headers
    },
    body: body
  };
}

function hashMsg(msg) {
  return crypto.createHash('sha256').update(JSON.stringify(msg), 'utf8').digest().toString('hex');
}

function getAuditKey(iso, fingerprint, tag) {
  return `audit/${iso.split('T')[0].replace(/-/g, '/')}/${fingerprint}|${encodeURIComponent(tag)}`;
}

function auditKeyToFingerprintAndTag(auditKey) {
  const f = auditKey.split('/').slice(-1)[0];
  const pipeIdx = f.indexOf('|');
  const fingerprint = f.slice(0, pipeIdx);
  const tag = f.slice(pipeIdx + 1);

  return {
    fingerprint,
    tag: decodeURIComponent(tag)
  };
}
