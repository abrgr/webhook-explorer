const {
  response,
  getUserFromEvent,
  getRequestPackageFolderKey,
  getRequestPackageKey
} = require('./common');
const S3 = require('aws-sdk/clients/s3');

const s3 = new S3({ apiVersion: '2019-09-21' });
const bucket = process.env.BUCKET_NAME;

exports.handler = async function handler(event) {
  const {
    uid,
    permissions: { canCreateRequestPackages }
  } = getUserFromEvent(event);

  if (!canCreateRequestPackages) {
    return response(401, {}, JSON.stringify({ error: 'Unauthorized' }));
  }

  try {
    const { expectNew, expectKey } = event.queryStringParameters || {};
    const { key: _key, ...packageConfig } = JSON.parse(event.body).package;

    const passedSoftCheck = await passesSoftCheckExpectedKey(
      expectNew,
      expectKey,
      packageConfig
    );
    if (!passedSoftCheck) {
      return response(409, {}, JSON.stringify({ error: 'Conflict' }));
    }
    await writeRequestPackage(uid, packageConfig);
    return response(200, {}, JSON.stringify({ success: true }));
  } catch (err) {
    console.error('Failed', err);
    return response(500, {}, JSON.stringify({ error: 'Unknown' }));
  }
};

async function writeRequestPackage(uid, packageConfig) {
  const key = getRequestPackageKey(uid, packageConfig);
  return s3
    .putObject({
      Bucket: bucket,
      Key: key,
      ContentType: 'application/json',
      Body: JSON.stringify(packageConfig)
    })
    .promise();
}

async function passesSoftCheckExpectedKey(expectNew, expectKey, packageConfig) {
  const { Contents } = s3
    .listObjectsV2({
      Bucket: bucket,
      Delimiter: '/',
      Prefix: getRequestPackageFolderKey(packageConfig)
    })
    .promise();

  if (expectNew && !!Contents && Contents.length) {
    return false;
  }

  if (
    expectKey &&
    (!Contents || !Contents.length || Contents[0].Key !== expectKey)
  ) {
    return false;
  }

  return true;
}
