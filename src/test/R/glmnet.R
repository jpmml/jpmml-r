library("glmnet")

source("util.R")

auto = loadAutoCsv("Auto")
auto$origin = as.integer(auto$origin)

auto_x = as.matrix(auto[, -ncol(auto)])
auto_y = auto[, ncol(auto)]

generateElNetAuto = function(){
	auto.glmnet = glmnet(x = auto_x, y = auto_y, family = "gaussian")
	print(auto.glmnet)

	auto.glmnet$lambda.s = auto.glmnet$lambda[23]

	mpg = predict(auto.glmnet, newx = auto_x, s = auto.glmnet$lambda.s, exact = TRUE)

	storeRds(auto.glmnet, "ElNetAuto")
	storeCsv(data.frame("_target" = mpg[, 1]), "ElNetAuto")
}

set.seed(42)

generateElNetAuto()

wine_quality = loadWineQualityCsv("WineQuality")

wine_quality_x = as.matrix(wine_quality[, -ncol(wine_quality)])
wine_quality_y = wine_quality[, ncol(wine_quality)]

generateElNetWineQuality = function(){
	wine_quality.glmnet = glmnet(x = wine_quality_x, y = wine_quality_y, family = "gaussian")
	print(wine_quality.glmnet)

	wine_quality.glmnet$lambda.s = wine_quality.glmnet$lambda[29]

	wine_quality = predict(wine_quality.glmnet, newx = wine_quality_x, s = wine_quality.glmnet$lambda.s, exact = TRUE)

	storeRds(wine_quality.glmnet, "ElNetWineQuality")
	storeCsv(data.frame("_target" = wine_quality[, 1]), "ElNetWineQuality")
}

set.seed(42)

generateElNetWineQuality()

visit = loadVisitCsv("Visit")
visit$edlevel = NULL

visit_x = as.matrix(visit[, -ncol(visit)])
visit_y = visit[, ncol(visit)]

generateFishNetVisit = function(){
	visit.glmnet = glmnet(x = visit_x, y = visit_y, family = "poisson")
	print(visit.glmnet)

	visit.glmnet$lambda.s = visit.glmnet$lambda[18]

	docvis = predict(visit.glmnet, newx = visit_x, s = visit.glmnet$lambda.s, exact = TRUE)

	storeRds(visit.glmnet, "FishNetVisit")
	storeCsv(data.frame("_target" = docvis[, 1]), "FishNetVisit")
}

set.seed(42)

generateFishNetVisit()
