storeRds = function(x, name){
	saveRDS(x, paste("../resources/rds/", name, ".rds", sep = ""))
}

loadCsv = function(name){
	return (read.csv(file = paste("../resources/csv/", name, ".csv", sep = ""), header = TRUE))
}

storeCsv = function(data, name){
	write.table(data, file = paste("../resources/csv/", name, ".csv", sep = ""), sep = ",", quote = FALSE, row.names = FALSE, col.names = gsub("X_target", "_target", names(data)))
}

loadAuditCsv = function(name){
	audit = loadCsv(name)
	audit$Adjusted = as.factor(audit$Adjusted)

	return (audit)
}

loadAutoCsv = function(name){
	auto = loadCsv(name)
	auto$origin = as.factor(auto$origin)

	return (auto)
}

loadIrisCsv = function(name){
	iris = loadCsv(name)

	return (iris)
}

loadWineColorCsv = function(name){
	wine_color = loadCsv(name)
	wine_color$color = as.factor(wine_color$color)

	return (wine_color)
}

loadWineQualityCsv = function(name){
	wine_quality = loadCsv(name)

	return (wine_quality)
}