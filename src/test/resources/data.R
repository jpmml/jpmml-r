library("rattle")

source("util.R")

createAudit = function(audit){
	audit$ID = NULL
	audit$IGNORE_Accounts = NULL
	audit$RISK_Adjustment = NULL

	audit$Deductions = as.logical(audit$Deductions > 0)

	names(audit)[ncol(audit)] = "Adjusted"

	storeCsv(audit, "csv/AuditNA.csv")

	audit = na.omit(audit)

	storeCsv(audit, "csv/Audit.csv")

	audit.matrix = data.frame(model.matrix(formula("Adjusted ~ ."), audit))
	names(audit.matrix) = gsub("\\.", "-", names(audit.matrix))

	# Delete the leading "X-Intercept" column
	audit.matrix = audit.matrix[, 2:ncol(audit.matrix)]

	storeCsv(audit.matrix, "csv/AuditMatrix.csv")
}

loadAuto = function(){
	data = read.table("http://archive.ics.uci.edu/ml/machine-learning-databases/auto-mpg/auto-mpg.data", quote = "\"", header = FALSE, na.strings = "?", row.names = NULL, col.names = c("mpg", "cylinders", "displacement", "horsepower", "weight", "acceleration", "model_year", "origin", "car_name"))

	return (data)
}

createAuto = function(){
	auto = loadAuto()

	auto$"car_name" = NULL

	# Move the "mpg" column to the last position
	auto = subset(auto, select = c(cylinders:origin, mpg))

	storeCsv(auto, "csv/AutoNA.csv")

	auto = na.omit(auto)

	storeCsv(auto, "csv/Auto.csv")
}

createIris = function(iris){
	storeCsv(iris, "csv/Iris.csv")
}

loadWineQuality = function(color){
	data = read.table(paste("http://archive.ics.uci.edu/ml/machine-learning-databases/wine-quality/winequality-", color, ".csv", sep = ""), sep = ";", header = TRUE)

	return (data)
}

createWineQuality = function(){
	red_data = loadWineQuality("red")
	white_data = loadWineQuality("white")

	wine_quality = rbind(red_data, white_data)

	storeCsv(wine_quality, "csv/WineQuality.csv")

	wine_color = rbind(red_data, white_data)
	wine_color$quality = NULL
	wine_color$color = "white"
	wine_color$color[1:nrow(red_data)] = "red"

	storeCsv(wine_color, "csv/WineColor.csv")
}

data(audit)
data(iris)

createAudit(audit)
createAuto()
createIris(iris)
createWineQuality()
