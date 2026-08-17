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

