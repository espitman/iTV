package app.itv.prototype.core

object CatalogPaging {
    const val PAGE_SIZE = 100

    fun nextOffset(offset: Int, rowCount: Int, pageSize: Int = PAGE_SIZE): Int? {
        if (rowCount <= 0 || rowCount < pageSize) return null
        return offset + pageSize
    }
}
