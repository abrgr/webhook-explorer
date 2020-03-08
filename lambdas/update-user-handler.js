const Cognito = require('aws-sdk/clients/cognitoidentityserviceprovider');
const { response, getUserFromEvent } = require('./common');

const cognito = new Cognito({ apiVersion: '2019-09-21' });
const poolId = process.env.COGNITO_USER_POOL_ID;

exports.handler = async function handler(event) {
  const { username } = event.pathParameters || {};
  const { actions } = JSON.parse(event.body) || {};
  const {
    permissions: { canAdminUsers }
  } = getUserFromEvent(event);

  if (!canAdminUsers) {
    return response(401, {}, JSON.stringify({ error: 'Unauthorized' }));
  }

  try {
    await Promise.all(actions.map(handleAction.bind(null, username)));

    return response(200, {}, JSON.stringify({ success: true }));
  } catch (err) {
    console.error('Failed to update user', err);
    const errors = err.errors ? err.errors : [err];
    const errMsgs = errors
      .filter(e => e.code === 'InvalidParameterType')
      .map(e => e.message);
    return response(
      400,
      {},
      JSON.stringify({
        error: { msg: 'Could not create user', messages: errMsgs }
      })
    );
  }
};

async function handleAction(username, { role, enabled }) {
  if (role) {
    return setUserRole(username, role);
  } else if (typeof enabled !== 'undefined') {
    if (enabled) {
      return enableUser(username);
    }
    return disableUser(username);
  }

  return null;
}

async function setUserRole(username, role) {
  return cognito
    .adminUpdateUserAttributes({
      UserPoolId: poolId,
      Username: username,
      UserAttributes: [
        {
          Name: 'custom:role',
          Value: role
        }
      ]
    })
    .promise();
}

async function enableUser(username) {
  return cognito
    .adminEnableUser({
      UserPoolId: poolId,
      Username: username
    })
    .promise();
}

async function disableUser(username) {
  return cognito
    .adminDisableUser({
      UserPoolId: poolId,
      Username: username
    })
    .promise();
}
