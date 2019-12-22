module.exports = {
  endOfToday,
  keyForParts,
  replaceKeyTag,
  folderForTag,
  response
};

function endOfToday(todayEpoch) {
  const tomorrow = new Date(todayEpoch);
  tomorrow.setDate(tomorrow.getDate() + 1);
  tomorrow.setHours(0);
  tomorrow.setMinutes(0);
  tomorrow.setSeconds(0);
  tomorrow.setMilliseconds(0);
  return tomorrow.getTime();
}

function keyForParts(folder, iso, method, host, path, reqId) {
  const ymd = iso.split('T')[0];
  const date = new Date(iso);
  const epoch = date.getTime();
  const sort = ('' + (endOfToday(epoch) - epoch)).padStart(8, '0');
  return `${folder}/${ymd.replace(/-/g, '/')}/${sort}:${encodeURIComponent(iso)}:${encodeURIComponent(method)}:${encodeURIComponent(host)}:${encodeURIComponent(path)}:${encodeURIComponent(reqId)}`;
}

function folderForTag(tag) {
  return `tags/${tag.replace(/[\/]/g, '$')}`;
}

function replaceKeyTag(newTag, sourceKey) {
  const [id, sort, d, m, y] = sourceKey.split('/').reverse();
  const key = `${folderForTag(tag)}/${y}/${m}/${d}/${sort}/${id}`;
  return key;
}

function response(statusCode, headers, body) {
  return {
    isBase64Encoded: false,
    statusCode,
    headers: {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Headers': 'Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token',
      'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
      ...headers
    },
    body: body
  };
}
