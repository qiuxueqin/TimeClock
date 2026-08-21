package com.timeclock.item.dto;

import java.util.List;

public record ItemPage(List<ItemView> items, int page, int pageSize, long total) {}
