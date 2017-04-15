library("e1071")

source("util.R")

audit = loadAuditCsv("Audit")

generateLibSVMFormulaAudit = function(){
	audit.svm = svm(Adjusted ~ ., data = audit)
	audit.svm$xlevels = stats:::.getXlevels(audit.svm$terms, audit)
	print(audit.svm)

	adjusted = predict(audit.svm, newdata = audit)

	storeRds(audit.svm, "LibSVMFormulaAudit")
	storeCsv(data.frame("Adjusted" = adjusted), "LibSVMFormulaAudit")
}

generateLibSVMAnomalyFormulaAudit = function(){
	audit.svm = svm(~ . - Adjusted, data = audit, type = "one-classification")
	audit.svm$xlevels = stats:::.getXlevels(audit.svm$terms, audit)
	print(audit.svm)

	outlier = predict(audit.svm, newdata = audit)
	outlier = ifelse(outlier, "true", "false")

	storeRds(audit.svm, "LibSVMAnomalyFormulaAudit")
	storeCsv(data.frame("outlier" = outlier), "LibSVMAnomalyFormulaAudit")
}

generateLibSVMFormulaAudit()
generateLibSVMAnomalyFormulaAudit()

auto = loadAutoCsv("Auto")

auto_x = auto[, -ncol(auto)]
auto_y = auto[, ncol(auto)]

generateLibSVMFormulaAuto = function(){
	auto.svm = svm(mpg ~ ., data = auto)
	auto.svm$xlevels = stats:::.getXlevels(auto.svm$terms, auto)
	print(auto.svm)

	mpg = predict(auto.svm, newdata = auto)

	storeRds(auto.svm, "LibSVMFormulaAuto")
	storeCsv(data.frame("mpg" = mpg), "LibSVMFormulaAuto")
}

generateLibSVMFormulaAuto()

auto_x$origin = as.integer(auto_x$origin)

generateLibSVMAuto = function(){
	auto.svm = svm(x = auto_x, y = auto_y, type = "nu-regression", kernel = "linear")
	print(auto.svm)

	mpg = predict(auto.svm, newdata = auto_x)

	storeRds(auto.svm, "LibSVMAuto")
	storeCsv(data.frame("_target" = mpg), "LibSVMAuto")
}

generateLibSVMAuto()

iris = loadIrisCsv("Iris")

iris_x = iris[, -ncol(iris)]
iris_y = iris[, ncol(iris)]

generateLibSVMFormulaIris = function(){
	iris.svm = svm(Species ~ ., data = iris)
	iris.svm$xlevels = stats:::.getXlevels(iris.svm$terms, iris)
	print(iris.svm)

	species = predict(iris.svm, newdata = iris)

	storeRds(iris.svm, "LibSVMFormulaIris")
	storeCsv(data.frame("Species" = species), "LibSVMFormulaIris")
}

generateLibSVMAnomalyFormulaIris = function(){
	iris.svm = svm(~ . - Species, data = iris, type = "one-classification")
	iris.svm$xlevels = stats:::.getXlevels(iris.svm$terms, iris)
	print(iris.svm)

	outlier = predict(iris.svm, newdata = iris)
	outlier = ifelse(outlier, "true", "false")

	storeRds(iris.svm, "LibSVMAnomalyFormulaIris")
	storeCsv(data.frame("outlier" = outlier), "LibSVMAnomalyFormulaIris")
}

generateLibSVMFormulaIris()
generateLibSVMAnomalyFormulaIris()

generateLibSVMIris = function(){
	iris.svm = svm(x = iris_x, y = iris_y, type = "nu-classification", kernel = "linear")
	print(iris.svm)

	species = predict(iris.svm, newdata = iris_x)

	storeRds(iris.svm, "LibSVMIris")
	storeCsv(data.frame("_target" = species), "LibSVMIris")
}

generateLibSVMIris()