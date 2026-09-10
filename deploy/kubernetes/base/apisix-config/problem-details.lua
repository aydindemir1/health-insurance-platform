local core = require("apisix.core")

local plugin_name = "problem-details"
local schema = { type = "object", properties = {}, additionalProperties = false }
local _M = { version = 0.1, priority = -9999, name = plugin_name, schema = schema }

function _M.check_schema(conf)
    return core.schema.check(schema, conf)
end

local titles = {
    [400] = "Bad Request", [401] = "Unauthorized", [403] = "Forbidden",
    [404] = "Not Found", [405] = "Method Not Allowed", [413] = "Payload Too Large",
    [429] = "Too Many Requests", [502] = "Bad Gateway",
    [503] = "Service Unavailable", [504] = "Gateway Timeout",
}

function _M.header_filter(_, ctx)
    local status = ngx.status
    local upstream_status = ngx.var.upstream_status
    if status < 400 or (upstream_status and upstream_status ~= "") then return end
    ctx.health_gateway_problem = {
        type = "https://health-insurance.local/problems/gateway-" .. status,
        title = titles[status] or "Gateway Error", status = status,
        detail = "The API gateway rejected the request before it reached an application service.",
        instance = ngx.var.uri,
        correlationId = ngx.req.get_headers()["X-Correlation-ID"],
    }
    ngx.header["Content-Type"] = "application/problem+json"
    ngx.header["Content-Length"] = nil
end

function _M.body_filter(_, ctx)
    if not ctx.health_gateway_problem then return end
    if not ngx.arg[2] then ngx.arg[1] = nil; return end
    ngx.arg[1] = core.json.encode(ctx.health_gateway_problem)
end

return _M
