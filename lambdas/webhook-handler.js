const S3 = require('aws-sdk/clients/s3');

const s3 = new S3({ apiVersion: '2019-09-21' });
const bucket = process.env.BUCKET_NAME;

exports.handler = async function handler(event, context) {
  const now = new Date();
  const iso = now.toISOString();
  const ymd = iso.split('T')[0];
  const key = `${ymd.replace(/-/g, '/')}/${iso}-${context.awsRequestId}`;
  await s3.putObject({
    Body: JSON.stringify(event),
    Bucket: bucket,
    Key: key
  }).promise();

  return {
    isBase64Encoded: false,
    statusCode: 200,
    headers: {},
    body: "OK"
  };
};
