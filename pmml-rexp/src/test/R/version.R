print("R version:")
print(paste(R.version$major, R.version$minor, sep = "."))

latest_versions = available.packages()

print("R package versions:")
packages = c("ada", "adabag", "caret", "caretEnsemble", "CHAID", "e1071", "earth", "elmNN", "evtree", "gbm", "glmnet", "IsolationForest", "mlr", "neuralnet", "nnet", "party", "partykit", "pls", "randomForest", "ranger", "rattle", "recipes", "rms", "rpart", "xgboost")
for(package in packages){
	version = tryCatch({
		packageVersion(package)
	}, error = function(cond){ print(cond) })

	latest_version = tryCatch({
		latest_versions[package, "Version"]
	}, error = function(cond){ print(cond) })

	print(paste(package, version, latest_version))
}

# update.packages()
