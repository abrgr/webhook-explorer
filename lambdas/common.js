const crypto = require('crypto');
const path = require('path');

const EXPECTED_AUD = process.env.EXPECTED_AUD;

module.exports = {
  endOfToday,
  keyForParts,
  partsForKey,
  replaceKeyTag,
  folderForTag,
  response,
  getUserFromEvent,
  getTagForFavorite,
  getPrivateTag,
  isPrivateTag,
  unNamespacedPrivateTag,
  isValidUserSpecifiedTag,
  isUserAuthorizedToReadFolder,
  isUserAuthorizedToWriteFolder,
  hashMsg,
  getAuditKey,
  auditKeyToFingerprintTagAndDate,
  cognitoUserToUser
};

function getUserFromEvent(event) {
  const {
    requestContext: {
      authorizer: {
        claims: {
          aud,
          "cognito:username": uid,
          "custom:role": role,
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
    uid,
    permissions: {
      canAdminUsers: role === 'admin'
    }
  };
}

function cognitoUserToUser(u) {
  return {
    username: u.Username,
    createdAt: u.UserCreateDate,
    enabled: u.Enabled,
    attributes: u.Attributes.reduce((attrs, { Name, Value }) => ({
      ...attrs,
      [Name]: Value
    }))
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

function keyForParts(folder, iso, method, host, path, fingerprint) {
  const ymd = iso.split('T')[0];
  const date = new Date(iso);
  const epoch = date.getTime();
  const sort = ('' + (endOfToday(epoch) - epoch)).padStart(8, '0');
  return `${folder}/${ymd.replace(/-/g, '/')}/${sort}:${encodeURIComponent(iso)}:${encodeURIComponent(method)}:${encodeURIComponent(host)}:${encodeURIComponent(path)}:${fingerprint}`;
}

function partsForKey(key) {
  const filename = path.basename(key);
  const [sort, encodedIso, method, encodedHost, encodedUrlPath, fingerprint] = filename.split(':');
  return {
    id: fingerprint,
    fingerprint,
    date: decodeURIComponent(encodedIso),
    path: decodeURIComponent(encodedUrlPath),
    host: decodeURIComponent(encodedHost),
    method
  };
}

function folderForTag(tag) {
  return `tags/${tag}`;
}

function isPrivateTag(tag) {
  // private tags are namespaced to users
  return tag.indexOf('/') >= 0;
}

function unNamespacedPrivateTag(namespacedPrivateTag) {
  return namespacedPrivateTag.split('/').slice(-1)[0];
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

function auditKeyToFingerprintTagAndDate(auditKey) {
  const [_, y, m, d, k] = auditKey.split('/');
  const pipeIdx = k.indexOf('|');
  const fingerprint = k.slice(0, pipeIdx);
  const tag = k.slice(pipeIdx + 1);

  return {
    fingerprint,
    tag: decodeURIComponent(tag),
    date: `${y}-${m}-${d}`
  };
}
