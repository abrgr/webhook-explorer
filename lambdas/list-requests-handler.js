const S3 = require('aws-sdk/clients/s3');

const s3 = new S3({ apiVersion: '2019-09-21' });
const bucket = process.env.BUCKET_NAME;

exports.handler = async function handler(event, context) {
  const { token, prefix } = event.queryStringParameters || {};

  const page = await nextListing(prefix || currentPrefix(), token);

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
  return ymd.replace(/-/g, '/');
}

async function nextListing(prefix, token) {
  if ( token ) {
    const nextPage = await getItemPage(prefix, token);
    if ( nextPage ) {
      return nextPage;
    }
  }

  const nextPrefix = await getNextPrefix(prefix);
  return await getItemPage(nextPrefix);
}

async function getItemPage(prefix, token) {
  const { Contents, NextContinuationToken } = await s3List(prefix, { ContinuationToken: token });
  if ( isEmpty(Contents) ) {
    return null;
  }

  return {
    items: Contents.map(c => c.Key),
    nextToken: NextContinuationToken
  };
}

async function getNextPrefix(prevPrefix) {
  const parts = prevPrefix.split('/');
  const [y, m, d] = parts.slice(-3);
  const resourcePrefix = parts.slice(0, -3).join('/');
  const ymPrefix = `${resourcePrefix}/${[y, m].join('/')}`;
  const { CommonPrefixes: daysForMonth } = await s3List(ymPrefix, { StartAfter: `${resourcePrefix}/${[y, m, d].join('/')}` });
  const nextDay = firstLessThan(daysForMonth.map(d => d.Prefix), ymPrefix);
  if ( nextDay ) {
    return nextDay;
  }

  const yPrefix = `${resourcePrefix}/${y}`;
  const { CommonPrefixes: monthsForYear } = await s3List(yPrefix, { StartAfter: `${resourcePrefix}/${[y, m].join('/')}` });
  const nextMonth = firstLessThan(monthsForYear.map(m => m.Prefix), yPrefix);
  if ( nextMonth ) {
    return getNextPrefix(nextMonth);
  }

  const { CommonPrefixes: years } = await s3List(resourcePrefix, { StartAfter: `${resourcePrefix}/${y}` });
  const nextYear = firstLessThan(years.map(m => m.Prefix), resourcePrefix);
  if ( nextYear ) {
    return getNextPrefix(nextYear);
  }

  return null;
}

async function s3List(prefix, opts) {
  return await s3.listObjectsV2({
    ...opts,
    Bucket: bucket,
    Delimiter: '/',
    Prefix: prefix
  }).promise();
}

function firstLessThan(l, x) {
  // assume l is sorted ascending
  if ( isEmpty(l) ) {
    return null;
  }

  const xIdx = l.indexOf(x);
  const nextIdx = xIdx - 1;
  if ( nextIdx >= l.length ) {
    return null;
  }
  return l[nextIdx];
}

function isEmpty(l) {
  return !l || !l.length;
}
