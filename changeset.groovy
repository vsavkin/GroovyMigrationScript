changeSet(author: 'Victor Savkinn', id: 'custom_sql') {
	sql 'ALTER TABLE domain ADD COLUMN newcolumn INT DEFAULT 10'
	rollback 'ALTER TABLE domain DROP COLUMN newcolumn'
}