package com.tntbbp.myminecraft.gui.economy;

/** 주식 거래소 GUI 페이지 계산(순수 로직, 단위 테스트 대상). 페이지 번호는 0부터. */
public final class StockPages {

    private StockPages() {
    }

    /** 전체 페이지 수. 종목이 없어도 1. */
    public static int pageCount(int totalItems, int perPage) {
        if (perPage <= 0) {
            throw new IllegalArgumentException("perPage must be positive");
        }
        if (totalItems <= 0) {
            return 1;
        }
        return (totalItems + perPage - 1) / perPage;
    }

    /** 페이지 번호를 {@code [0, pageCount-1]}로 맞춘다(종목이 줄어 마지막 페이지가 사라진 경우 등). */
    public static int clampPage(int page, int totalItems, int perPage) {
        int last = pageCount(totalItems, perPage) - 1;
        return Math.max(0, Math.min(page, last));
    }

    /** 이 페이지 첫 종목의 목록 번호(포함). */
    public static int fromIndex(int page, int perPage) {
        return page * perPage;
    }

    /** 이 페이지 마지막 종목 다음 번호(제외). */
    public static int toIndex(int page, int perPage, int totalItems) {
        return Math.min(totalItems, (page + 1) * perPage);
    }

    public static boolean hasPrevious(int page) {
        return page > 0;
    }

    public static boolean hasNext(int page, int totalItems, int perPage) {
        return page < pageCount(totalItems, perPage) - 1;
    }
}
