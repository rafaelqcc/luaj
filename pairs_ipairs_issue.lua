print("=== __pairs ===")

local t = setmetatable({}, {
    __pairs = function()
        print("__pairs called")
        local done = false
        return function()
            if done then
                return nil
            end
            done = true
            return "hello", "world"
        end, nil, nil
    end
})

for k, v in pairs(t) do
    print(k, v)
end

print("=== __ipairs ===")

local t2 = setmetatable({}, {
    __ipairs = function()
        print("__ipairs called")
        local i = 0
        return function()
            i = i + 1
            if i > 3 then
                return nil
            end
            return i, "value " .. i
        end, nil, nil
    end
})

for i, v in ipairs(t2) do
    print(i, v)
end
