const {
  response,
  getUserFromEvent
} = require('./common');
const S3 = require('aws-sdk/clients/s3');
const DynamoDB = require('aws-sdk/clients/dynamodb');

const documentClient = new DynamoDB.DocumentClient({ apiVersion: '2019-09-21' });
const s3 = new S3({ apiVersion: '2019-09-21' });
const bucket = process.env.BUCKET_NAME;
const table = process.env.HANDLERS_TABLE_NAME;

exports.handler = async function handler(event, context) {
  const { permissions: { canCreateHandlers }} = getUserFromEvent(event);

  if ( !canCreateHandlers ) {
    return response(401, {}, JSON.stringify({ error: 'Unauthorized' }));
  }

  const { proto, method, domain, matchType, path } = event.queryStringParameters || {};

  const { Item: handler } = await documentClient.get({
    TableName: table,
    Key: {
      protoMethod: `${proto}:${method}`,
      domainPath: `${domain}${path}`
    }
  }).promise();

  const { prefixKey, exactKey } = handler;
  const key = matchType === 'exact' ? exactKey : prefixKey;

  if ( !key ) {
    return response(404, {}, JSON.stringify({ error: 'Missing' }));
  }

  const { Body: fullHandlerJson } = await s3.getObject({
    Bucket: bucket,
    Key: key
  }).promise();

  const fullHandler = JSON.parse(fullHandlerJson);

  return response(200, {}, JSON.stringify({ handler: fullHandler }));
};
