const {
  response,
  getUserFromEvent
} = require('./common');
const DynamoDB = require('aws-sdk/clients/dynamodb');

const documentClient = new DynamoDB.DocumentClient({ apiVersion: '2019-09-21' });
const table = process.env.HANDLERS_TABLE_NAME;

exports.handler = async function handler(event) {
  const { permissions: { canCreateHandlers } } = getUserFromEvent(event);

  if ( !canCreateHandlers ) {
    return response(401, {}, JSON.stringify({ error: 'Unauthorized' }));
  }

  const { proto, method, token } = event.queryStringParameters || {};
  const { LastEvaluatedKey: nextToken, Items: rawHandlers } = await documentClient.query({
    TableName: table,
    KeyConditionExpression: '#protoMethod = :protoMethod',
    ExpressionAttributeNames: {
      '#protoMethod': 'protoMethod'
    },
    ExpressionAttributeValues: {
      ':protoMethod': `${proto}:${method}`
    },
    ...token ? { ExclusiveStartKey: token } : {}
  }).promise();

  console.log(JSON.stringify({ rawHandlers }));

  const handlers = rawHandlers.map(({ domainPath, exactKey, prefixKey, tPath, protoMethod }) => {
    const [proto, method] = protoMethod.split(':');
    const firstSlashIdx = domainPath.indexOf('/');
    const domain = domainPath.slice(0, firstSlashIdx);
    const path = domainPath.slice(firstSlashIdx);
    return {
      proto,
      method,
      domain,
      path,
      hasExactHandler: !!exactKey,
      hasPrefixHandler: !!prefixKey,
      tPath
    };
  });

  const result = {
    nextToken,
    handlers: handlers.filter(h => h.hasExactHandler || h.hasPrefixHandler)
  };

  return response(200, { 'Cache-Control': 'max-age=30' }, JSON.stringify(result));
};
