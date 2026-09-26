-- One prebuilt deterministic request per run. The callback records only
-- same-window counts and 100 ms bins; it does not construct request bytes.
local ffi = require('ffi')
ffi.cdef[[struct timespec { long tv_sec; long tv_nsec; };
int clock_gettime(int, struct timespec *);]]
local clock = ffi.new('struct timespec[1]')
local function monotonic_seconds()
  ffi.C.clock_gettime(1, clock) -- CLOCK_MONOTONIC
  return tonumber(clock[0].tv_sec) + tonumber(clock[0].tv_nsec) / 1e9
end

local threads = {}
local request_bytes
local window_start
local window_end
window_requests = 0
non_2xx = 0
bucket_text = ''
last_bucket = -1
bucket_count = 0

function setup(thread)
  threads[#threads + 1] = thread
end

function init(args)
  local route = assert(os.getenv('MOSAIC_ROUTE'))
  local body = assert(os.getenv('MOSAIC_BODY'))
  window_start = assert(tonumber(os.getenv('MOSAIC_WINDOW_START')))
  window_end = assert(tonumber(os.getenv('MOSAIC_WINDOW_END')))
  request_bytes = wrk.format('POST', '/' .. route,
                             { ['Content-Type'] = 'application/json' }, body)
end

function request()
  local now = monotonic_seconds()
  if now >= window_start and now < window_end then
    window_requests = window_requests + 1
    local bucket = math.floor((now - window_start) * 10)
    if bucket ~= last_bucket then
      if last_bucket >= 0 then
        bucket_text = bucket_text .. last_bucket .. ':' .. bucket_count .. ','
      end
      last_bucket = bucket
      bucket_count = 0
    end
    bucket_count = bucket_count + 1
  end
  return request_bytes
end

function response(status, headers, body)
  if status < 200 or status >= 300 then
    non_2xx = non_2xx + 1
  end
end

function done(summary, latency, requests)
  for index, thread in ipairs(threads) do
    local text = thread:get('bucket_text') or ''
    local last = thread:get('last_bucket') or -1
    if last >= 0 then
      text = text .. last .. ':' .. thread:get('bucket_count')
    end
    print(string.format('MOSAIC_WINDOW thread=%d count=%d bins=%s',
                        index, thread:get('window_requests') or 0, text))
    print(string.format('MOSAIC_NON2XX thread=%d count=%d',
                        index, thread:get('non_2xx') or 0))
  end
end
