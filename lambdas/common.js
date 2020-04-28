const crypto = require('crypto');
const path = require('path');
const http = require('http');
const https = require('https');
const url = require('url');
const zlib = require('zlib');
const stableStringify = require('json-stable-stringify');

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
  cognitoUserToUser,
  parseRequestCookies,
  parseResponseCookies,
  fingerprintTagAndDateToUserVisibleTags,
  getHandlerKey,
  executeRequest,
  getRequestPackageKey,
  getRequestPackageFolderKey
};

function getUserFromEvent(event) {
  const {
    requestContext: {
      authorizer: {
        claims: { aud, 'cognito:username': uid, 'custom:role': role, email }
      }
    }
  } = event;

  if (aud !== EXPECTED_AUD) {
    console.error('Bad authorizer audience', { event });
    throw new Error('Unauthorized');
  }

  return {
    email,
    uid,
    permissions: {
      canAdminUsers: role === 'admin',
      canCreateHandlers: role === 'admin' || role === 'eng',
      canExecuteArbitraryRequests: role === 'admin' || role === 'eng',
      canCreateRequestPackages: role === 'admin' || role === 'eng'
    }
  };
}

function cognitoUserToUser(u) {
  return u.Attributes.reduce(
    (attrs, { Name, Value }) => ({
      ...attrs,
      [Name.replace('custom:', '')]: Value
    }),
    {
      username: u.Username,
      createdAt: u.UserCreateDate,
      enabled: u.Enabled
    }
  );
}

function isUserAuthorizedToReadFolder(uid, folder) {
  // TODO: || isAdmin(uid);
  return isUserAuthorizedToWriteFolder(uid, folder);
}

function isUserAuthorizedToWriteFolder(uid, folder) {
  // folders either look like "all", "tags/public-tag", or "tags/*uid*/private-tag"
  const folderParts = folder.split('/');
  if (folderParts.length < 3) {
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

function keyForParts(folder, iso, method, host, path, status, fingerprint) {
  const ymd = iso.split('T')[0];
  const date = new Date(iso);
  const epoch = date.getTime();
  const sort = ('' + (endOfToday(epoch) - epoch)).padStart(8, '0');
  return `${folder}/${ymd.replace(/-/g, '/')}/${sort}:${encodeURIComponent(
    iso
  )}:${encodeURIComponent(method)}:${encodeURIComponent(
    host
  )}:${encodeURIComponent(path)}:${status}:${fingerprint}`;
}

function partsForKey(key) {
  const filename = path.basename(key);
  const [
    _sort,
    encodedIso,
    method,
    encodedHost,
    encodedUrlPath,
    status,
    fingerprint
  ] = filename.split(':');
  return {
    id: fingerprint,
    fingerprint,
    date: decodeURIComponent(encodedIso),
    path: decodeURIComponent(encodedUrlPath),
    host: decodeURIComponent(encodedHost),
    method,
    status
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

function response(statusCode, headers, body, isBase64Encoded) {
  return {
    isBase64Encoded: !!isBase64Encoded,
    statusCode,
    multiValueHeaders: Object.keys(headers).reduce(
      (hs, h) => ({
        ...hs,
        [h]: Array.isArray(headers[h]) ? headers[h] : [headers[h]]
      }),
      {
        'Access-Control-Allow-Origin': ['*'],
        'Access-Control-Allow-Headers': [
          'Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token'
        ],
        'Access-Control-Allow-Methods': ['GET,POST,OPTIONS']
      }
    ),
    body: body
  };
}

function hashMsg(msg) {
  const toHash = stableStringify(msg);
  return crypto
    .createHash('sha256')
    .update(toHash, 'utf8')
    .digest()
    .toString('hex');
}

function getAuditKey(iso, fingerprint, tag) {
  return `audit/${iso
    .split('T')[0]
    .replace(/-/g, '/')}/${fingerprint}|${encodeURIComponent(tag)}`;
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

function getHandlerKey(handlerConfig) {
  const hash = hashMsg(handlerConfig);
  return `handlers/${hash}`;
}

function getRequestPackageFolderKey(packageConfig) {
  const { name } = packageConfig;
  return `packages/${name}`;
}

function descendingS3Date(date) {
  const y = `${10000 - date.getFullYear()}`.padStart(4, '0');
  const m = `${13 - (date.getMonth() + 1)}`.padStart(2, '0');
  const d = `${32 - date.getDate()}`.padStart(2, '0');
  const h = `${25 - date.getHours()}`.padStart(2, '0');
  const mm = `${61 - date.getMinutes()}`.padStart(2, '0');
  const s = `${61 - date.getSeconds()}`.padStart(2, '0');
  const ms = `${1000 - date.getMilliseconds()}`.padStart(3, '0');
  return `${y}-${m}-${d}T${h}:${mm}:${s}.${ms}|${date.toISOString()}`;
}

function getRequestPackageKey(uid, packageConfig) {
  const encodedUid = encodeURIComponent(uid);
  const hash = hashMsg(packageConfig);
  return `${getRequestPackageFolderKey(packageConfig)}/${descendingS3Date(
    new Date()
  )}|${encodedUid}|${hash}`;
}

function parseRequestCookies(cookieStr) {
  const pairs = (cookieStr || '').split(';');
  return pairs.reduce((cookies, pair) => {
    const [k, v] = pair.trim().split('=');
    if (!k || !v) {
      return cookies;
    }
    return {
      ...cookies,
      [k]: { value: decodeURIComponent(v) }
    };
  }, {});
}

function parseResponseCookies(cookieStrs) {
  return (cookieStrs || []).reduce((cookies, cookieStr) => {
    const parts = (cookieStr || '').split(';');
    if (!parts.length) {
      return cookies;
    }

    const [k, v] = parts[0].trim().split('=');
    const tags = parts.slice(1).reduce((tags, p) => {
      const [tagK, tagV] = p.trim().split('=');
      return {
        ...tags,
        [tagK]: tagV ? decodeURIComponent(tagV) : true
      };
    }, {});

    return {
      ...cookies,
      [k]: {
        value: decodeURIComponent(v),
        tags
      }
    };
  }, {});
}

function fingerprintTagAndDateToUserVisibleTags(uid, items) {
  const favTag = getTagForFavorite(uid);
  const filteredItems = items.filter(({ tag }) =>
    isUserAuthorizedToReadFolder(uid, folderForTag(tag))
  );
  return filteredItems.reduce((tagsForFingerprint, { fingerprint, tag }) => {
    const prev = tagsForFingerprint[fingerprint] || {
      fav: false,
      privateTags: [],
      publicTags: []
    };

    if (tag === favTag) {
      prev.fav = true;
    } else if (isPrivateTag(tag)) {
      prev.privateTags = prev.privateTags.concat([unNamespacedPrivateTag(tag)]);
    } else {
      prev.publicTags = prev.publicTags.concat([tag]);
    }

    tagsForFingerprint[fingerprint] = prev;

    return tagsForFingerprint;
  }, {});
}

async function executeRequest(method, remoteUrl, headers, body) {
  return new Promise((resolve, reject) => {
    const parsedUrl = url.parse(remoteUrl);
    const opts = {
      auth: parsedUrl.auth || void 0,
      hostname: parsedUrl.hostname,
      port: parsedUrl.port || void 0,
      path: parsedUrl.path,
      method,
      headers
    };
    const isHttps = (parsedUrl.protocol || 'https').indexOf('https') >= 0;
    const protoHandler = isHttps ? https : http;
    const req = protoHandler.request(opts, res => {
      let respBody = Buffer.from([]);
      res.on('data', chunk => {
        respBody = Buffer.concat([respBody, chunk]);
      });
      res.on('end', () => {
        const enc = res.headers['content-encoding'];
        const ct = res.headers['content-type'];
        const decompressedRespBody = decompress(enc, respBody);
        const isUtf = !!/^text|^multipart|[\/](javascript|json|edn|xml|xhtml)/.exec(
          ct
        );
        return resolve({
          status: res.statusCode,
          headers: res.headers,
          body: decompressedRespBody.toString(isUtf ? 'utf8' : 'base64'),
          isBase64Encoded: !isUtf
        });
      });
    });
    req.on('error', reject);

    if (body) {
      req.write(body);
    }
    req.end();
  });
}

function decompress(encoding, body) {
  switch (encoding) {
    case 'br':
      return zlib.brotliDecompressSync(body);
    case 'gzip':
      return zlib.gunzipSync(body);
    case 'deflate':
      return zlib.deflateSync(body);
    default:
      return body;
  }
}
