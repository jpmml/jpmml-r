library("rattle")

source("util.R")

# See http://stackoverflow.com/a/27454361/1808924
insertNA = function(df, frac = 0.20){
	mod = function(x){
		return (x[sample(c(TRUE, NA), prob = c(1 - frac, frac), size = length(x), replace = TRUE)])
	}

	df = as.data.frame(lapply(df, FUN = mod))

	return (df)
}

createAudit = function(audit){
	audit$ID = NULL
	audit$IGNORE_Accounts = NULL
	audit$RISK_Adjustment = NULL

	audit$Deductions = as.logical(audit$Deductions > 0)

	names(audit)[ncol(audit)] = "Adjusted"

	audit = na.omit(audit)

	storeCsv(audit, "Audit")

	audit_x = audit[, -ncol(audit)]
	audit_y = audit[, ncol(audit)]

	auditNA = cbind(insertNA(audit_x), "Adjusted" = audit_y)

	storeCsv(auditNA, "AuditNA")
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

	auto = na.omit(auto)

	storeCsv(auto, "Auto")

	auto_x = auto[, -ncol(auto)]
	auto_y = auto[, ncol(auto)]

	autoNA = cbind(insertNA(auto_x, frac = 0.05), "mpg" = auto_y)

	storeCsv(autoNA, "AutoNA")
}

createIris = function(iris){
	storeCsv(iris, "Iris")

	iris_x = iris[, -ncol(iris)]
	iris_y = iris[, ncol(iris)]

	irisNA = cbind(insertNA(iris_x, frac = 0.10), "Species" = iris_y)

	storeCsv(irisNA, "IrisNA")
}

loadModeChoice = function(){
	data = read.table("http://www.apollochoicemodelling.com/files/examples/data/apollo_modeChoiceData.csv", sep = ",", header = TRUE)

	return (data)
}

createModeChoice = function(){
	modeChoice = loadModeChoice()

	modeChoice = subset(modeChoice, modeChoice$SP == 1)
	modeChoice$RP = NULL
	modeChoice$RP_journey = NULL

	storeCsv(modeChoice, "ModeChoice")
}

createNMES1988 = function(nmes){
	storeCsv(nmes, "NMES1988")
}

loadWineQuality = function(color){
	data = read.table(paste("http://archive.ics.uci.edu/ml/machine-learning-databases/wine-quality/winequality-", color, ".csv", sep = ""), sep = ";", header = TRUE)

	return (data)
}

createWineQuality = function(){
	red_data = loadWineQuality("red")
	white_data = loadWineQuality("white")

	wine = rbind(red_data, white_data)

	wine_x = wine[, -ncol(wine)]
	wine_y = wine[, ncol(wine)]

	wineNA = cbind(insertNA(wine_x), "quality" = wine_y)

	storeCsv(wine, "WineQuality")
	storeCsv(wineNA, "WineQualityNA")

	wine$quality = NULL
	wineNA$quality = NULL

	wine$color = "white"
	wine$color[1:nrow(red_data)] = "red"
	wine$color = as.factor(wine$color)
	wineNA$color = wine$color

	storeCsv(wine, "WineColor")
	storeCsv(wineNA, "WineColorNA")
}

data(audit)
data(iris)
data("NMES1988", package = "AER")

createAudit(audit)
createAuto()
createIris(iris)
createModeChoice()
createNMES1988(NMES1988)
createWineQuality()
