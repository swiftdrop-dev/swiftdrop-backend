package com.swiftdrop.parcel.dto.response;

import java.util.List;

public record ParcelPageResponse<T>(List<T> content, int page, int size, boolean hasNext) {
}
