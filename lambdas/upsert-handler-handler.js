const {
  response,
  getUserFromEvent,
  getHandlerKey
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

  const { expectNew, expectKey } = event.queryStringParameters || {};
  const handlerConfig = JSON.parse(event.body);
  const {
    matchType,
    domain,
    path: templatedPath
  } = handlerConfig;

  const path = templatedPath.replace(/[{][^}]+[}]/g, '{}');
  const key = getHandlerKey(handlerConfig);

  console.log('s3 put', {
    Bucket: bucket,
    Key: key,
    ContentType: 'application/json',
    Body: JSON.stringify(handlerConfig)
  });

  try {
    await s3.putObject({
      Bucket: bucket,
      Key: key,
      ContentType: 'application/json',
      Body: JSON.stringify(handlerConfig)
    }).promise();
  } catch ( err ) {
    console.error('Failed to write to s3', err);
    return response(500, {}, JSON.stringify({ error: 'Unknown' }));
  }

  const record = {
    domain,
    path,
    matchType,
    key
  };

  try {
    console.log('put doc', {
      ...getConditionParams(expectNew, expectKey),
      TableName: table,
      Item: record
    });

    await documentClient.put({
      ...getConditionParams(expectNew, expectKey),
      TableName: table,
      Item: record
    }).promise();
  } catch ( err ) {
    if ( err.code === 'ConditionalCheckFailedException' ) {
      return response(409, {}, JSON.stringify({ error: 'Conflict' }));
    }

    console.error('Failed to update dynamo', err);

    return response(500, {}, JSON.stringify({ error: 'Unknown' }));
  }

  return response(200, {}, JSON.stringify({ success: true }));
};

function getConditionParams(expectNew, expectKey) {
  if ( expectNew ) {
    return {
      ConditionExpression: 'attribute_not_exists(#key)',
      ExpressionAttributeNames: {
        '#key': 'key'
      }
    };
  }

  if ( expectKey ) {
    return {
      ConditionExpression: '#key = :oldKey',
      ExpressionAttributeNames: {
        '#key': 'key'
      },
      ExpressionAttributeValues: {
        ':oldKey': expectKey
      }
    };
  }

  return {};
}
