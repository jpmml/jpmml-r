print("R version:")
print(paste(R.version$major, R.version$minor, sep = "."))

latest_versions = available.packages()

print("R package versions:")
packages = c("ada", "adabag", "caret", "caretEnsemble", "CHAID", "e1071", "earth", "elmNN", "gbm", "glmnet", "IsolationForest", "neuralnet", "nnet", "party", "partykit", "pls", "randomForest", "ranger", "rattle", "recipes", "rms", "rpart", "xgboost")
for(package in packages){
	version = packageVersion(package)
	if(!(package %in% c("CHAID", "IsolationForest"))){
		latest_version = latest_versions[package, "Version"]
	} else {
		latest_version = "NA"
	}

	print(paste(package, version, latest_version))
}

# update.packages()
