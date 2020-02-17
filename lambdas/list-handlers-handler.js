const {
  response,
  getUserFromEvent,
  cognitoUserToUser,
} = require('./common');
const S3 = require('aws-sdk/clients/s3');
const DynamoDB = require('aws-sdk/clients/dynamodb');

const documentClient = new DynamoDB.DocumentClient({ apiVersion: '2019-09-21' });
const s3 = new S3({ apiVersion: '2019-09-21' });
const bucket = process.env.BUCKET_NAME;
const table = process.env.HANDLERS_TABLE_NAME;
const ONE_HOUR_IN_SECONDS = 60 * 60;

exports.handler = async function handler(event, context) {
  const { permissions: { canCreateHandlers }} = getUserFromEvent(event);

  if ( !canCreateHandlers ) {
    return response(401, {}, JSON.stringify({ error: 'Unauthorized' }));
  }

  const { proto, method, token } = event.queryStringParameters || {};
  const { LastEvaluatedKey: nextToken, Items: handlers } = await documentClient.query({
    TableName: table,
    KeyConditionExpression: '#protoMethod = :protoMethod',
    ExpressionAttributeNames: {
      '#protoMethod': 'protoMethod'
    },
    ExpressionAttributeValues: {
      ':protoMethod': `${proto}:${method}`
    },
    ...(token ? { ExclusiveStartKey: token } : {})
  }).promise();

  const result = {
    nextToken,
    handlers: await Promise.all(handlers.map(async function ({ domainPath, exactKey, prefixKey }) {
      const firstSlashIdx = domainPath.indexOf('/');
      const domain = domainPath.slice(0, firstSlashIdx);
      const path = domainPath.slice(firstSlashIdx);
      const exactDataUrl = exactKey ? await getSignedUrlForKey(exactKey) : null;
      const prefixDataUrl = prefixKey ? await getSignedUrlForKey(prefixKey) : null;
      return {
        domain,
        path,
        exactDataUrl,
        prefixDataUrl
      };
    }))
  };

  return response(200, { 'Cache-Control': 'max-age=30' }, JSON.stringify(result));
};

async function getSignedUrlForKey(key) {
  const method = 'getObject';
  const params = {
    Bucket: bucket,
    Key: key,
    Expires: ONE_HOUR_IN_SECONDS
  };
  return new Promise((resolve, reject) => {
    s3.getSignedUrl(method, params, (err, url) => {
      if ( err ) {
        return reject(err);
      }

      resolve(url);
    });
  });
}
