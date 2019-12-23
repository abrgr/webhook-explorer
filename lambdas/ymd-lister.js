const S3 = require('aws-sdk/clients/s3');

const s3 = new S3({ apiVersion: '2019-09-21' });
const bucket = process.env.BUCKET_NAME;

exports.getNextListing = async function getNextListing(folder, ymd, token, makeItemFromKey) {
  return await nextListing(folder, normalizePrefix(ymd) || currentPrefix(), token, makeItemFromKey);
};

function normalizePrefix(ymd) {
  if ( !ymd ) {
    return null;
  }

  return ymd.endsWith('/') ? ymd : `${ymd}/`;
}

function currentPrefix() {
  const now = new Date();
  const iso = now.toISOString();
  const ymd = iso.split('T')[0];
  return normalizePrefix(ymd.replace(/-/g, '/'));
}

async function nextListing(folder, prefix, token, makeItemFromKey) {
  if ( token ) {
    const nextPage = await getItemPage(folder, prefix, token, makeItemFromKey);
    if ( nextPage ) {
      return nextPage;
    }
  }

  const pageForPrefix = await getItemPage(folder, prefix, undefined, makeItemFromKey);
  if ( pageForPrefix ) {
    return pageForPrefix;
  }

  const nextPrefix = await getNextPrefix(folder, prefix);
  return await getItemPage(folder, nextPrefix, undefined, makeItemFromKey);
}

async function getItemPage(folder, prefix, token, makeItemFromKey) {
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

  const items = await Promise.all(Contents.map(c => makeItemFromKey(c.Key)));
  return {
    folder,
    items,
    nextReq: nextReq.ymd ? nextReq : null
  };
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

function removeFolderFromPrefix(folder, prefix) {
  const prefixFolder = `${folder}/`;
  if ( prefix.startsWith(prefixFolder) ) {
    return prefix.slice(prefixFolder.length);
  }

  return prefix;
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
