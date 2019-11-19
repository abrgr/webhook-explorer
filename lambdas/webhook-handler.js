const S3 = require('aws-sdk/clients/s3');

const s3 = new S3({ apiVersion: '2019-09-21' });
const bucket = process.env.BUCKET_NAME;

exports.handler = async function handler(event, context) {
  const now = new Date();
  const iso = now.toISOString();
  const epoch = now.getTime();
  const sort = ('' + (endOfToday(epoch) - epoch)).padStart(8, '0');
  const ymd = iso.split('T')[0];
  const method = event.httpMethod;
  const path = encodeURIComponent(event.path);
  const headers = event.headers || {};
  const host = encodeURIComponent(headers.Host || headers.host);
  const body = event.body;
  const key = `all/${ymd.replace(/-/g, '/')}/${sort}:${encodeURIComponent(iso)}:${method}:${host}:${path}:${context.awsRequestId}`;
  const msg = {
    host,
    path,
    method,
    iso,
    req: {
      headers,
      body
    },
    res: {
      headers: {},
      body: "OK"
    }
  };
  await s3.putObject({
    Body: JSON.stringify(msg),
    Bucket: bucket,
    Key: key,
    ContentType: 'application/json'
  }).promise();

  return {
    isBase64Encoded: false,
    statusCode: 200,
    headers: {},
    body: "OK"
  };
};

function endOfToday(todayEpoch) {
  const tomorrow = new Date(todayEpoch);
  tomorrow.setDate(tomorrow.getDate() + 1);
  tomorrow.setHours(0);
  tomorrow.setMinutes(0);
  tomorrow.setSeconds(0);
  tomorrow.setMilliseconds(0);
  return tomorrow.getTime();
}
