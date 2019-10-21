const S3 = require('aws-sdk/clients/s3');

const s3 = new S3({ apiVersion: '2019-09-21' });
const bucket = process.env.BUCKET_NAME;

exports.handler = async function handler(event, context) {
  const { folder, ymd, token } = event.queryStringParameters || {};

  const page = await nextListing(folder, normalizePrefix(ymd) || currentPrefix(), token);

  return {
    isBase64Encoded: false,
    statusCode: 200,
    headers: {},
    body: JSON.stringify(page)
  };
};

function currentPrefix() {
  const now = new Date();
  const iso = now.toISOString();
  const ymd = iso.split('T')[0];
  return normalizePrefix(ymd.replace(/-/g, '/'));
}

async function nextListing(folder, prefix, token) {
  console.log('nextListing', { folder, prefix, token });
  if ( token ) {
    console.log('Using token', { token });
    const nextPage = await getItemPage(folder, prefix, token);
    if ( nextPage ) {
      return nextPage;
    }
  }

  const pageForPrefix = await getItemPage(folder, prefix);
  console.log('pageForPrefix', { pageForPrefix, folder, prefix });
  if ( pageForPrefix ) {
    return pageForPrefix;
  }

  const nextPrefix = await getNextPrefix(folder, prefix);
  console.log('nextPrefix', { nextPrefix, folder, prefix, token });
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

  return {
    folder,
    items: Contents.map(c => c.Key),
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
    console.log('daysForMonth', { daysForMonth, folder, prevPrefix, nextDay, y, m, d, flt: `${ymPrefix}${d}/` });
    if ( nextDay ) {
      return nextDay.slice(folder.length + 1); // remove the leading "<folder>/"
    }
  }

  if ( !!y ) {
    const yPrefix = `${folder}/${y}/`;
    const { CommonPrefixes: monthsForYear } = await s3List(yPrefix);
    const nextMonth = !!m
                    ? firstLessThan(monthsForYear.map(m => m.Prefix), `${yPrefix}${m}/`)
                    : get(last(monthsForYear), 'Prefix');
    console.log('monthsForYear', { monthsForYear, folder, prevPrefix, nextMonth, y });
    if ( nextMonth ) {
      return getNextPrefix(folder, nextMonth);
    }
  }

  const { CommonPrefixes: years } = await s3List(`${folder}/`);
  const nextYear = !!y
                 ? firstLessThan(years.map(m => m.Prefix), `${folder}/${y}/`)
                 : get(last(years), 'Prefix');
  console.log('years', { years, folder, prevPrefix });
  if ( nextYear ) {
    return getNextPrefix(folder, nextYear);
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

  console.log('s3list', params);

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

  console.log('FLT', { l, x, lt });

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
