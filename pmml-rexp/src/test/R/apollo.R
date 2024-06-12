library("apollo")

source("util.R")

apollo_initialise()

# See http://www.apollochoicemodelling.com/files/examples/1%20MNL/MNL_RP.r
generateMNLModeChoice = function(){
	database <<- loadModeChoiceCsv("ModeChoice")

	apollo_control <<- list(
		modelName = "MNL_RP",
		modelDescr = "Simple MNL model on mode choice RP data",
		indivID = "ID",
		outputDirectory = "/dev/null"
	)

	apollo_beta <<- c(
		asc_car   = 0,
		asc_bus   = 0,
		asc_air   = 0,
		asc_rail  = 0,
		b_tt_car  = 0,
		b_tt_bus  = 0,
		b_tt_air  = 0,
		b_tt_rail = 0,
		b_access  = 0,
		b_cost    = 0
	)

	apollo_fixed <<- c("asc_car")

	apollo_inputs = apollo_validateInputs()

	apollo_probabilities = function(apollo_beta, apollo_inputs, functionality="estimate"){

		### Attach inputs and detach after function exit
		apollo_attach(apollo_beta, apollo_inputs)
		on.exit(apollo_detach(apollo_beta, apollo_inputs))

		### Create list of probabilities P
		P = list()

		### List of utilities: these must use the same names as in mnl_settings, order is irrelevant
		V = list()
		V[["car"]]  = asc_car  + b_tt_car  * time_car                           + b_cost * cost_car
		V[["bus"]]  = asc_bus  + b_tt_bus  * time_bus  + b_access * access_bus  + b_cost * cost_bus
		V[["air"]]  = asc_air  + b_tt_air  * time_air  + b_access * access_air  + b_cost * cost_air
		V[["rail"]] = asc_rail + b_tt_rail * time_rail + b_access * access_rail + b_cost * cost_rail

		### Define settings for MNL model component
		mnl_settings = list(
			alternatives  = c(car=1, bus=2, air=3, rail=4),
			#avail = list(car=av_car, bus=av_bus, air=av_air, rail=av_rail),
			choiceVar = choice,
			utilities = V
		)

		### Compute probabilities using MNL model
		P[["model"]] = apollo_mnl(mnl_settings, functionality)

		### Take product across observation for same individual
		P = apollo_panelProd(P, apollo_inputs, functionality)

		### Prepare and return outputs of function
		P = apollo_prepareProb(P, apollo_inputs, functionality)

		return(P)
	}

	model = apollo_estimate(apollo_beta, apollo_fixed, apollo_probabilities, apollo_inputs)
	print(model)

	choices = c("car", "bus", "air", "rail")

	choice = apollo_prediction(model, apollo_probabilities, apollo_inputs)
	choice = choice[choices]
	colnames(choice) = lapply(choices, function(x){ paste("probability(", x, ")", sep = "")})
	choice$choice = apply(choice, 1, function(x) { choices[which.max(x)] })

	storeRds(model, "MNLModeChoice")
	storeCsv(choice, "MNLModeChoice")
}

# See http://www.apollochoicemodelling.com/files/examples/1%20MNL/MNL_RP.r
generateNLModeChoice = function(){
	database <<- loadModeChoiceCsv("ModeChoice")

	apollo_control <<- list(
		modelName = "NL_RP",
		modelDescr = "Simple NL model on mode choice RP data",
		indivID = "ID",
		outputDirectory = "/dev/null"
	)

	apollo_beta <<- c(
		asc_car   = 0,
		asc_bus   = 0,
		asc_air   = 0,
		asc_rail  = 0,
		b_tt_car  = 0,
		b_tt_bus  = 0,
		b_tt_air  = 0,
		b_tt_rail = 0,
		b_access  = 0,
		b_cost    = 0
	)

	apollo_fixed <<- c("asc_car")

	apollo_inputs = apollo_validateInputs()

	apollo_probabilities = function(apollo_beta, apollo_inputs, functionality="estimate"){

		### Attach inputs and detach after function exit
		apollo_attach(apollo_beta, apollo_inputs)
		on.exit(apollo_detach(apollo_beta, apollo_inputs))

		### Create list of probabilities P
		P = list()

		### List of utilities: these must use the same names as in nl_settings, order is irrelevant
		V = list()
		V[["car"]]  = asc_car  + b_tt_car  * time_car                           + b_cost * cost_car
		V[["bus"]]  = asc_bus  + b_tt_bus  * time_bus  + b_access * access_bus  + b_cost * cost_bus
		V[["air"]]  = asc_air  + b_tt_air  * time_air  + b_access * access_air  + b_cost * cost_air
		V[["rail"]] = asc_rail + b_tt_rail * time_rail + b_access * access_rail + b_cost * cost_rail

		### Specify nests for NL model
		nlNests = list(root = 1, PT = 0.75)

		### Specify tree structure for NL model
		nlStructure = list()
		nlStructure[["root"]] = c("car", "PT")
		nlStructure[["PT"]] = c("bus", "air", "rail")

		### Define settings for MNL model component
		nl_settings = list(
			alternatives  = c(car=1, bus=2, air=3, rail=4),
			#avail = list(car=av_car, bus=av_bus, air=av_air, rail=av_rail),
			choiceVar = choice,
			utilities = V,
			nlNests = nlNests,
			nlStructure = nlStructure
		)

		### Compute probabilities using MNL model
		P[["model"]] = apollo_nl(nl_settings, functionality)

		### Take product across observation for same individual
		P = apollo_panelProd(P, apollo_inputs, functionality)

		### Prepare and return outputs of function
		P = apollo_prepareProb(P, apollo_inputs, functionality)

		return(P)
	}

	model = apollo_estimate(apollo_beta, apollo_fixed, apollo_probabilities, apollo_inputs)
	print(model)

	choices = c("car", "bus", "air", "rail")

	choice = apollo_prediction(model, apollo_probabilities, apollo_inputs)
	choice = choice[choices]
	colnames(choice) = lapply(choices, function(x){ paste("probability(", x, ")", sep = "")})
	choice$choice = apply(choice, 1, function(x) { choices[which.max(x)] })

	storeRds(model, "NLModeChoice")
	storeCsv(choice, "NLModeChoice")
}

rm(list = c("database", "apollo_control", "apollo_beta", "apollo_fixed"))

set.seed(42)

generateMNLModeChoice()

rm(list = c("database", "apollo_control", "apollo_beta", "apollo_fixed"))

set.seed(42)

generateNLModeChoice()
