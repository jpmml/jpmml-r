library("caret")
library("gbm")

source("util.R")

audit = loadAuditCsv("csv/AuditNA.csv")

# Error in gbm.fit: Deductions is not of type numeric, ordered, or factor
audit$Deductions = NULL

audit_x = audit[, -ncol(audit)]
audit_y = audit[, ncol(audit)]
audit_y = as.numeric(audit_y == "1")

predictGBMAudit = function(audit.gbm){
	adjusted = predict(audit.gbm, newdata = audit_x, n.trees = 100)

	probability_1 = predict(audit.gbm, newdata = audit_x, type = "response", n.trees = 100)
	probability_0 = (1 - probability_1)

	result = data.frame("Adjusted" = adjusted, "probability_0" = probability_0, "probability_1" = probability_1)

	return (result)
}

generateGBMAdaBoostAuditNA = function(){
	audit.gbm = gbm.fit(x = audit_x, y = audit_y, distribution = "adaboost", interaction.depth = 3, shrinkage = 0.1, n.trees = 100, response.name = "Adjusted")
	print(audit.gbm)

	storeProtoBuf(audit.gbm, "pb/GBMAdaBoostAuditNA.pb")
	storeCsv(predictGBMAudit(audit.gbm), "csv/GBMAdaBoostAuditNA.csv")
}

generateGBMBernoulliAuditNA = function(){
	audit.gbm = gbm.fit(x = audit_x, y = audit_y, distribution = "bernoulli", interaction.depth = 3, shrinkage = 0.1, n.trees = 100, response.name = "Adjusted")
	print(audit.gbm)

	storeProtoBuf(audit.gbm, "pb/GBMBernoulliAuditNA.pb")
	storeCsv(predictGBMAudit(audit.gbm), "csv/GBMBernoulliAuditNA.csv")
}

set.seed(42)

generateGBMAdaBoostAuditNA()
generateGBMBernoulliAuditNA()

auto = loadAutoCsv("csv/AutoNA.csv")

auto_x = auto[, -ncol(auto)]
auto_y = auto[, ncol(auto)]

generateGBMFormulaAutoNA = function(){
	auto.gbm = gbm(mpg ~ ., data = auto, interaction.depth = 3, shrinkage = 0.1, n.trees = 100)
	print(auto.gbm)

	mpg = predict(auto.gbm, newdata = auto, n.trees = 100)

	storeProtoBuf(auto.gbm, "pb/GBMFormulaAutoNA.pb")
	storeCsv(data.frame("mpg" = mpg), "csv/GBMFormulaAutoNA.csv")
}

generateGBMAutoNA = function(){
	auto.gbm = gbm.fit(x = auto_x, y = auto_y, distribution = "gaussian", interaction.depth = 3, shrinkage = 0.1, n.trees = 100, response.name = "mpg")
	print(auto.gbm)

	mpg = predict(auto.gbm, newdata = auto_x, n.trees = 100)

	storeProtoBuf(auto.gbm, "pb/GBMAutoNA.pb")
	storeCsv(data.frame("mpg" = mpg), "csv/GBMAutoNA.csv")
}

set.seed(42)

generateGBMFormulaAutoNA()
generateGBMAutoNA()

auto.caret = auto
auto.caret$origin = as.integer(auto.caret$origin)

generateTrainGBMFormulaAutoNA = function(){
	auto.train = train(mpg ~ ., data = auto.caret, method = "gbm", response.name = "mpg")
	print(auto.train)

	mpg = predict(auto.train, newdata = auto.caret, na.action = na.pass)

	storeProtoBuf(auto.train, "pb/TrainGBMFormulaAutoNA.pb")
	storeCsv(data.frame("mpg" = mpg), "csv/TrainGBMFormulaAutoNA.csv")
}

generateTrainGBMAutoNA = function(){
	auto.train = train(x = auto_x, y = auto_y, method = "gbm", response.name = "mpg")
	print(auto.train)

	mpg = predict(auto.train, newdata = auto_x)

	storeProtoBuf(auto.train, "pb/TrainGBMAutoNA.pb")
	storeCsv(data.frame("mpg" = mpg), "csv/TrainGBMAutoNA.csv")
}

set.seed(42)

generateTrainGBMFormulaAutoNA()
generateTrainGBMAutoNA()