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

	mpg = predict(auto.glmnet, newx = auto_x, auto.glmnet$lambda.s, exact = TRUE)

	storeRds(auto.glmnet, "GLMNetAuto")
	storeCsv(data.frame("_target" = mpg[, 1]), "GLMNetAuto")
}

set.seed(42)

generateGLMNetAuto()