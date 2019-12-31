const Cognito = require('aws-sdk/clients/cognitoidentityserviceprovider');
const {
  response,
  getUserFromEvent,
  cognitoUserToUser
} = require('./common');

const cognito = new Cognito({ apiVersion: '2019-09-21' });
const poolId = process.env.COGNITO_USER_POOL_ID;

exports.handler = async function handler(event, context) {
  const { username } = event.pathParameters || {};
  const { actions } = JSON.parse(event.body) || {};
  const { permissions: { canAdminUsers }} = getUserFromEvent(event);

  if ( !canAdminUsers ) {
    return response(401, {}, JSON.stringify({ error: 'Unauthorized' }));
  }

  try {
    await Promise.all(
      actions.map(handleAction.bind(null, username))
    );

    return response(200, {}, JSON.stringify({ success: true }));
  } catch ( err ) {
    console.error('Failed to update user', err);
    const errors = err.errors ? err.errors : [err];
    const errMsgs = errors.filter(e => e.code === 'InvalidParameterType')
                          .map(e => e.message);
    return response(400, {}, JSON.stringify({ error: { msg: "Could not create user", messages: errMsgs }}));
  }
};

async function handleAction(username, { role, enabled }) {
  if ( role ) {
    return await setUserRole(username, role);
  } else if ( typeof enabled !== 'undefined' ) {
    if ( enabled ) {
      return await enableUser(username);
    } else {
      return await disableUser(username);
    }
  }
}

async function setUserRole(username, role) {
  return await cognito.adminUpdateUserAttributes({
    UserPoolId: poolId,
    Username: username,
    UserAttributes: [
      {
        Name: 'custom:role',
        Value: role
      }
    ]
  }).promise();
}

async function enableUser(username) {
  return await cognito.adminEnableUser({
    UserPoolId: poolId,
    Username: username
  }).promise();
}

async function disableUser(username) {
  return await cognito.adminDisableUser({
    UserPoolId: poolId,
    Username: username
  }).promise();
}
