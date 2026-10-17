const now = new Date()
const deadline = new Date(now.getTime() + 6 * 60 * 60 * 1000)
deadline.setMinutes(Math.ceil(deadline.getMinutes() / 5) * 5, 0, 0)

const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
const deadlineDay = new Date(
  deadline.getFullYear(),
  deadline.getMonth(),
  deadline.getDate(),
)
const dayDifference = Math.round((deadlineDay - today) / (24 * 60 * 60 * 1000))
const hour24 = deadline.getHours()
const hour12 = hour24 % 12 || 12
const minute = deadline.getMinutes()
const dayPrefix = dayDifference === 0
  ? "오늘"
  : dayDifference === 1
    ? "내일"
    : `${deadline.getMonth() + 1}월 ${deadline.getDate()}일`

output.deadline = {
  hourSelector: `${hour12}시 정각`,
  minuteSelector: `${minute}분`,
  meridiem: hour24 < 12 ? "오전" : "오후",
  visibleLabel: `${dayPrefix} ${hour24 < 12 ? "오전" : "오후"} ${hour12}:${String(minute).padStart(2, "0")}까지`,
}
