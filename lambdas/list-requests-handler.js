const path = require('path');
const crypto = require('crypto');
const S3 = require('aws-sdk/clients/s3');
const {
  response,
  folderForTag,
  getUserFromEvent,
  getTagForFavorite,
  isUserAuthorizedToReadFolder
} = require('./common');

const s3 = new S3({ apiVersion: '2019-09-21' });
const bucket = process.env.BUCKET_NAME;
const ONE_HOUR_IN_SECONDS = 60 * 60;

exports.handler = async function handler(event, context) {
  const { folder, fav, tag, ymd, token } = event.queryStringParameters || {};
  const { uid } = getUserFromEvent(event);
  const resolvedTag = fav
                    ? getTagForFavorite(uid)
                    : tag;
  const resolvedFolder = resolvedTag
                       ? folderForTag(resolvedTag)
                       : folder;

  if ( !isUserAuthorizedToReadFolder(uid, resolvedFolder) ) {
    return response(401, {}, JSON.stringify({ error: 'Unauthorized' }));
  }

  const page = await nextListing(resolvedFolder, normalizePrefix(ymd) || currentPrefix(), token);
  const cacheSeconds = (ymd || token)
                     ? 300
                     : 5;

  return response(200, { 'Cache-Control': `max-age=${cacheSeconds}` }, JSON.stringify(page));
};

function currentPrefix() {
  const now = new Date();
  const iso = now.toISOString();
  const ymd = iso.split('T')[0];
  return normalizePrefix(ymd.replace(/-/g, '/'));
}

async function nextListing(folder, prefix, token) {
  if ( token ) {
    const nextPage = await getItemPage(folder, prefix, token);
    if ( nextPage ) {
      return nextPage;
    }
  }

  const pageForPrefix = await getItemPage(folder, prefix);
  if ( pageForPrefix ) {
    return pageForPrefix;
  }

  const nextPrefix = await getNextPrefix(folder, prefix);
  return await getItemPage(folder, nextPrefix);
}

async function getItemPage(folder, prefix, token) {
  const { Contents, NextContinuationToken } = await s3List(`${folder}/${prefix}`, { ContinuationToken: token });
  if ( isEmpty(Contents) ) {
    return null;
  }

  const nextReq = NextContinuationToken
                ? {
                  folder,
                  ymd: prefix,
                  token: NextContinuationToken
                } : {
                  folder,
                  ymd: await getNextPrefix(folder, prefix)
                };

  const items = await Promise.all(Contents.map(c => makeItem(c.Key)));
  return {
    folder,
    items,
    nextReq: nextReq.ymd ? nextReq : null
  };
}

async function makeItem(key) {
  const filename = path.basename(key);
  const [, encodedIso, method, encodedHost, encodedUrlPath] = filename.split(':');

  return {
    id: crypto.createHash('sha256').update(key, 'utf8').digest().toString('hex'),
    dataUrl: await getSignedUrl('getObject', { Bucket: bucket, Key: key, Expires: ONE_HOUR_IN_SECONDS }),
    date: decodeURIComponent(encodedIso),
    path: decodeURIComponent(encodedUrlPath),
    host: decodeURIComponent(encodedHost),
    method
  };
}

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

function removeFolderFromPrefix(folder, prefix) {
  const prefixFolder = `${folder}/`;
  if ( prefix.startsWith(prefixFolder) ) {
    return prefix.slice(prefixFolder.length);
  }

  return prefix;
}

async function getNextPrefix(folder, prevPrefix) {
  const [y, m, d] = prevPrefix.split('/');

  if ( !!y && !!m ) {
    const ymPrefix = `${folder}/${[y, m].join('/')}/`;
    const { CommonPrefixes: daysForMonth } = await s3List(ymPrefix);
    const nextDay = !!d
                  ? firstLessThan(daysForMonth.map(d => d.Prefix), `${ymPrefix}${d}/`)
                  : get(last(daysForMonth), 'Prefix');
    if ( nextDay ) {
      return removeFolderFromPrefix(folder, nextDay);
    }
  }

  if ( !!y ) {
    const yPrefix = `${folder}/${y}/`;
    const { CommonPrefixes: monthsForYear } = await s3List(yPrefix);
    const nextMonth = !!m
                    ? firstLessThan(monthsForYear.map(m => m.Prefix), `${yPrefix}${m}/`)
                    : get(last(monthsForYear), 'Prefix');
    if ( nextMonth ) {
      return getNextPrefix(folder, removeFolderFromPrefix(folder, nextMonth));
    }
  }

  const { CommonPrefixes: years } = await s3List(`${folder}/`);
  const nextYear = !!y
                 ? firstLessThan(years.map(m => m.Prefix), `${folder}/${y}/`)
                 : get(last(years), 'Prefix');
  if ( nextYear ) {
    return getNextPrefix(folder, removeFolderFromPrefix(folder, nextYear));
  }

  return null;
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

function normalizePrefix(ymd) {
  if ( !ymd ) {
    return null;
  }

  return ymd.endsWith('/') ? ymd : `${ymd}/`;
}

function firstLessThan(l, x) {
  // assume l is sorted ascending
  const lt = (l || []).filter(i => i < x);

  if ( isEmpty(lt) ) {
    return null;
  }

  return last(lt);
}

function get(m, k) {
  return !!m ? m[k] : null;
}

function last(l) {
  return l[l.length - 1];
}

function isEmpty(l) {
  return !l || !l.length;
}
