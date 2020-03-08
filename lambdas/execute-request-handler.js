const { response, getUserFromEvent, executeRequest } = require('./common');

exports.handler = async function handler(event) {
  const {
    req: { method, url: remoteUrl, headers, body }
  } = JSON.parse(event.body) || {};
  const {
    permissions: { canExecuteArbitraryRequests }
  } = getUserFromEvent(event);

  if (!canExecuteArbitraryRequests) {
    return response(401, {}, JSON.stringify({ error: 'Unauthorized' }));
  }

  const res = await executeRequest(
    method,
    remoteUrl,
    headers,
    body && Buffer.from(body, 'utf8')
  );

  return response(200, {}, JSON.stringify({ res }));
};
