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

exports.handler = async function handler(event) {
  const { permissions: { canCreateHandlers } } = getUserFromEvent(event);

  if ( !canCreateHandlers ) {
    return response(401, {}, JSON.stringify({ error: 'Unauthorized' }));
  }

  const { expectNew, expectKey } = event.queryStringParameters || {};
  const handlerConfig = JSON.parse(event.body).handler;
  const {
    matchType,
    proto,
    domain,
    method,
    path: templatedPath
  } = handlerConfig;

  const path = templatedPath.replace(/[{][^}]+[}]/g, '{}');
  const domainPath = `${domain}${path}`;
  const protoMethod = `${proto.toLowerCase()}:${method.toLowerCase()}`;
  const key = getHandlerKey(handlerConfig);

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

  try {
    const keyName = `${matchType}Key`;
    const suffixCountName = `${matchType}SuffixCount`;
    const expParams = {
      ExpressionAttributeNames: {
        '#key': keyName,
        '#tPath': 'tPath'
      },
      ExpressionAttributeValues: {
        ':key': key,
        ':tPath': templatedPath
      }
    };
    const conditionalParams = getConditionParams(keyName, expectNew, expectKey, expParams);

    await documentClient.update({
      TableName: table,
      Key: {
        domainPath,
        protoMethod
      },
      UpdateExpression: 'set #key = :key, #tPath = :tPath',
      ...conditionalParams
    }).promise();

    const pathParts = path.slice(1).split('/');
    await Promise.all(pathParts.map((_, i) => {
      const prefixKey = domain + '/' + pathParts.slice(0, i).join('/');
      return documentClient.update({
        TableName: table,
        Key: {
          domainPath: prefixKey,
          protoMethod
        },
        UpdateExpression: 'set #suffixCount = if_not_exists(#suffixCount, :z) + :i',
        ExpressionAttributeNames: {
          '#suffixCount': suffixCountName
        },
        ExpressionAttributeValues: {
          ':i': 1,
          ':z': 0
        }
      }).promise();
    }));
  } catch ( err ) {
    if ( err.code === 'ConditionalCheckFailedException' ) {
      return response(409, {}, JSON.stringify({ error: 'Conflict' }));
    }

    console.error('Failed to update dynamo', err);

    return response(500, {}, JSON.stringify({ error: 'Unknown' }));
  }

  return response(200, {}, JSON.stringify({ success: true }));
};

function getConditionParams(keyName, expectNew, expectKey, expParams) {
  if ( expectNew ) {
    return {
      ...expParams,
      ConditionExpression: 'attribute_not_exists(#key)',
      ExpressionAttributeNames: {
        ...expParams.ExpressionAttributeNames,
        '#key': keyName
      }
    };
  }

  if ( expectKey ) {
    return {
      ...expParams,
      ConditionExpression: '#key = :oldKey',
      ExpressionAttributeNames: {
        ...expParams.ExpressionAttributeNames,
        '#key': keyName
      },
      ExpressionAttributeValues: {
        ...expParams.ExpressionAttributeValues,
        ':oldKey': expectKey
      }
    };
  }

  return {
    ...expParams
  };
}
