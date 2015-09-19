library("r2pmml")

storeProtoBuf = function(x, file){
	x.rexp = r2pmml:::.clean(x)

	saveProtoBuf(x.rexp, file)
}

loadCsv = function(file){
	return (read.csv(file = file, header = TRUE))
}

storeCsv = function(data, file){
	write.table(data, file = file, sep = ",", quote = FALSE, row.names = FALSE, col.names = gsub("X_target", "_target", names(data)))
}

loadAuditCsv = function(file){
	audit = loadCsv(file)
	audit$Adjusted = as.factor(audit$Adjusted)

	return (audit)
}

loadAutoCsv = function(file){
	auto = loadCsv(file)
	auto$origin = as.factor(auto$origin)

	return (auto)
}

loadIrisCsv = function(file){
	iris = loadCsv(file)

	return (iris)
}

loadWineColorCsv = function(file){
	wine_color = loadCsv(file)
	wine_color$color = as.factor(wine_color$color)

	return (wine_color)
}

loadWineQualityCsv = function(file){
	wine_quality = loadCsv(file)

	return (wine_quality)
}