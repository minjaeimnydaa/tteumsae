const earthRadiusKm = 6371;

export function haversineKm(from, to) {
  const toRadians = (degrees) => (degrees * Math.PI) / 180;
  const latitudeDelta = toRadians(to.latitude - from.latitude);
  const longitudeDelta = toRadians(to.longitude - from.longitude);
  const fromLatitude = toRadians(from.latitude);
  const toLatitude = toRadians(to.latitude);

  const a =
    Math.sin(latitudeDelta / 2) ** 2 +
    Math.cos(fromLatitude) *
      Math.cos(toLatitude) *
      Math.sin(longitudeDelta / 2) ** 2;
  return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function estimateMinutes(from, to, transport) {
  const distance = haversineKm(from, to);
  const isWalking = transport === "WALK";
  const speedKmh = isWalking ? 4.5 : 35;
  const roadFactor = isWalking ? 1.2 : 1.35;
  const fixedMinutes = isWalking ? 0 : 2;
  return Math.max(1, Math.ceil((distance * roadFactor * 60) / speedKmh + fixedMinutes));
}

export function estimateRoute(start, destination, place, transport) {
  const placeCoordinates = {
    latitude: place.latitude,
    longitude: place.longitude
  };
  const firstLegMinutes = estimateMinutes(start, placeCoordinates, transport);
  const secondLegMinutes = estimateMinutes(placeCoordinates, destination, transport);
  const directMinutes = estimateMinutes(start, destination, transport);

  return {
    firstLegMinutes,
    secondLegMinutes,
    directMinutes,
    detourMinutes: Math.max(0, firstLegMinutes + secondLegMinutes - directMinutes),
    provider: "ESTIMATE"
  };
}

export function createSearchBounds(start, destination, transport) {
  const padding = transport === "WALK" ? 0.055 : 0.22;
  return {
    minLatitude: Math.min(start.latitude, destination.latitude) - padding,
    maxLatitude: Math.max(start.latitude, destination.latitude) + padding,
    minLongitude: Math.min(start.longitude, destination.longitude) - padding,
    maxLongitude: Math.max(start.longitude, destination.longitude) + padding
  };
}

export function createPathBounds(path, paddingKm) {
  const latitudePadding = paddingKm / 111;
  const meanLatitude = path.reduce((sum, point) => sum + point.latitude, 0) /
    Math.max(path.length, 1);
  const longitudePadding = paddingKm /
    (111 * Math.max(0.1, Math.cos(meanLatitude * Math.PI / 180)));
  return {
    minLatitude: Math.min(...path.map((point) => point.latitude)) - latitudePadding,
    maxLatitude: Math.max(...path.map((point) => point.latitude)) + latitudePadding,
    minLongitude: Math.min(...path.map((point) => point.longitude)) - longitudePadding,
    maxLongitude: Math.max(...path.map((point) => point.longitude)) + longitudePadding
  };
}

export function distanceToPathKm(point, path) {
  if (
    !Number.isFinite(point?.latitude) ||
    !Number.isFinite(point?.longitude) ||
    path.length === 0
  ) {
    return Infinity;
  }
  if (path.length === 1) return haversineKm(point, path[0]);

  // ponytail: local projection is sufficient for an 8 km corridor; use PostGIS if wider regions need exact geometry.
  const latitudeScale = 111.32;
  const longitudeScale = latitudeScale * Math.cos(point.latitude * Math.PI / 180);
  const projected = path.map((pathPoint) => ({
    x: (pathPoint.longitude - point.longitude) * longitudeScale,
    y: (pathPoint.latitude - point.latitude) * latitudeScale
  }));
  let minimum = Infinity;
  for (let index = 0; index + 1 < projected.length; index += 1) {
    const from = projected[index];
    const to = projected[index + 1];
    const dx = to.x - from.x;
    const dy = to.y - from.y;
    const lengthSquared = dx * dx + dy * dy;
    const ratio = lengthSquared === 0
      ? 0
      : Math.max(0, Math.min(1, -(from.x * dx + from.y * dy) / lengthSquared));
    minimum = Math.min(
      minimum,
      Math.hypot(from.x + ratio * dx, from.y + ratio * dy)
    );
  }
  return minimum;
}
