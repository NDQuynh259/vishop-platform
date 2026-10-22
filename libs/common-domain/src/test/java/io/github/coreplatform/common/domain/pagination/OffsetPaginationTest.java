package io.github.coreplatform.common.domain.pagination;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OffsetPaginationTest {

    // #region defaultValues
    @Test
    void defaultValuesWhenZeroOrNegative() {
        OffsetPagination p = OffsetPagination.of(0, 0);
        assertEquals(OffsetPagination.DEFAULT_PAGE, p.page());
        assertEquals(OffsetPagination.DEFAULT_LIMIT, p.limit());
        assertEquals(0L, p.offset());
        assertEquals(0, p.pageIndex());

        OffsetPagination negative = OffsetPagination.of(-5, -10);
        assertEquals(OffsetPagination.DEFAULT_PAGE, negative.page());
        assertEquals(OffsetPagination.DEFAULT_LIMIT, negative.limit());
    }
    // #endregion

    // #region limitClamping
    @Test
    void limitClampedToMax() {
        OffsetPagination p = OffsetPagination.of(1, 500);
        assertEquals(OffsetPagination.MAX_LIMIT, p.limit());
    }
    // #endregion

    // #region offsetCalculation
    @Test
    void offsetCalculation() {
        OffsetPagination p1 = OffsetPagination.of(1, 20);
        assertEquals(0L, p1.offset());
        assertEquals(0, p1.pageIndex());

        OffsetPagination p2 = OffsetPagination.of(2, 20);
        assertEquals(20L, p2.offset());
        assertEquals(1, p2.pageIndex());

        OffsetPagination p5 = OffsetPagination.of(5, 50);
        assertEquals(200L, p5.offset());
        assertEquals(4, p5.pageIndex());
    }
    // #endregion
}
