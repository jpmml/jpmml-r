library("party")

source("util.R")

predictClassificationTree = function(ctree, data, targetName, probabilityNames){
	target = data[, targetName]

	classes = ctree@predict_response(newdata = data, type = "response")
	probabilities = ctree@predict_response(newdata = data, type = "prob")
	nodes = ctree@predict_response(newdata = data, type = "node")

	# Convert from list of lists to data.frame
	probabilities = data.frame(matrix(unlist(probabilities), nrow = nrow(data), byrow = TRUE))
	names(probabilities) = probabilityNames

	result = data.frame(classes)
	names(result) = c(targetName)
	result = data.frame(result, probabilities, "nodeId" = nodes, check.names = FALSE)

	return (result)
}

audit = loadAuditCsv("Audit")

generateBinaryTreeAudit = function(){
	audit.ctree = ctree(Adjusted ~ ., data = audit)
	print(audit.ctree)

	storeRds(audit.ctree, "BinaryTreeAudit")
	storeCsv(predictClassificationTree(audit.ctree, audit, "Adjusted", c("probability(0)", "probability(1)")), "BinaryTreeAudit")
}

set.seed(42)

generateBinaryTreeAudit()

auto = loadAutoCsv("Auto")

generateBinaryTreeAuto = function(){
	auto.ctree = ctree(mpg ~ ., data = auto)
	print(auto.ctree)

	mpg = auto.ctree@predict_response(newdata = auto, type = "response")
	nodes = auto.ctree@predict_response(newdata = auto, type = "node")

	storeRds(auto.ctree, "BinaryTreeAuto")
	storeCsv(data.frame("mpg" = mpg, "nodeId" = nodes), "BinaryTreeAuto")
}

set.seed(42)

generateBinaryTreeAuto()

iris = loadIrisCsv("Iris")

generateBinaryTreeIris = function(){
	iris.ctree = ctree(Species ~ ., data = iris)
	print(iris.ctree)

	storeRds(iris.ctree, "BinaryTreeIris")
	storeCsv(predictClassificationTree(iris.ctree, iris, "Species", c("probability(setosa)", "probability(versicolor)", "probability(virginica)")), "BinaryTreeIris")
}

set.seed(42)

generateBinaryTreeIris()