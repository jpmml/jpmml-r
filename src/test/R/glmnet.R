library("glmnet")

source("util.R")

auto = loadAutoCsv("Auto")
auto$origin = as.integer(auto$origin)

auto_x = as.matrix(auto[, -ncol(auto)])
auto_y = as.matrix(auto[, ncol(auto)])

generateGLMNetAuto = function(){
	auto.glmnet = glmnet(x = auto_x, y = auto_y, family = "gaussian")
	print(auto.glmnet)

	auto.glmnet$lambda.s = auto.glmnet$lambda[23]

	mpg = predict(auto.glmnet, newx = auto_x, s = auto.glmnet$lambda.s, exact = TRUE)

	storeRds(auto.glmnet, "GLMNetAuto")
	storeCsv(data.frame("_target" = mpg[, 1]), "GLMNetAuto")
}

set.seed(42)

generateGLMNetAuto()

visit = loadVisitCsv("Visit")
visit$edlevel = NULL

visit_x = as.matrix(visit[, -ncol(visit)])
visit_y = as.matrix(visit[, ncol(visit)])

generateGLMNetVisit = function(){
	visit.glmnet = glmnet(x = visit_x, y = visit_y, family = "poisson")
	print(visit.glmnet)

	visit.glmnet$lambda.s = visit.glmnet$lambda[18]

	docvis = predict(visit.glmnet, newx = visit_x, s = visit.glmnet$lambda.s, exact = TRUE)

	storeRds(visit.glmnet, "GLMNetVisit")
	storeCsv(data.frame("_target" = docvis[, 1]), "GLMNetVisit")
}

set.seed(42)

generateGLMNetVisit()
