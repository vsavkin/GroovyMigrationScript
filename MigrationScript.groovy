package test

/**
 * Performs migrations via liquidbase library
 */
class Migrator {
	private ant = new AntBuilder()
	private db

	Migrator(Map db) {
		ant.path(id: 'main.classpath') {
			fileset(dir: 'lib') {
				include(name: '*.jar')
			}
		}

		ant.taskdef(resource: "liquibasetasks.properties") {
			classpath refid: "main.classpath"
		}

		this.db = [driver: db.driver, url: db.url, username: db.username,
			password: db.password, classpathref: 'main.classpath']
	}

	def tag(String tag) {
		ant.tagDatabase db + [tag: tag]
	}

	def migrate(String file) {
		ant.updateDatabase db + [changeLogFile: file, promptOnNonLocalDatabase: false, dropFirst: false]
	}

	def rollback(String file, String tag) {
		ant.rollbackDatabase db + [changeLogFile: file, rollbackTag: tag]
	}

	def rollback(String file, int count) {
		ant.rollbackDatabase db + [changeLogFile: file, rollbackCount: count]
	}
}

/**
 * Generates a temporary xml files containing a changeset.
 */
class GroovyToChangeset {
	def generateXmlChangeSetFile(File groovyFile) {
		def script = createScript(groovyFile.text)
		def shell = new GroovyShell(new Binding())
		def xml = shell.evaluate(script)
		println xml
		saveToTmpFile xml
	}
	
	private saveToTmpFile(String xml){
		def file = new File('compiledGroovyChangeSet.xml')
		file.deleteOnExit()
		file.withWriter {
			it << xml
		}
		file
	}

	private createScript(String script) {
		"""def writer = new StringWriter()
		def xml = new groovy.xml.MarkupBuilder(writer)
		xml.mkp.xmlDeclaration(version:"1.0", encoding:"UTF-8")
		xml.databaseChangeLog(xmlns: "http://www.liquibase.org/xml/ns/dbchangelog/1.9",
			"xmlns:xsi": "http://www.w3.org/2001/XMLSchema-instance", "xsi:schemaLocation":
			"http://www.liquibase.org/xml/ns/dbchangelog/1.9 http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-1.9.xsd"
		) {

			${script}

		}
		writer.toString()
		"""
	}
}

/**
 * Parses all input arguments and performs all operations
 */
class Starter {
	def run(List args){
		assert args.size() >= 2, 'Not all arguments are specified'
		assert new File(args[0]).exists(), "Properties file doens't exist"
		
		def db = parseDbConfig(args[0])
		Migrator migrator = new Migrator(db)
		def command = args[1]

		if(command == 'migrate'){
			assert new File(args[2]).exists(), "Changeset file doesn't exist"
			def filename = getChangeSetFilename(args[2])

			println "Performing migrations from ${filename}"
			migrator.migrate filename

		} else if (command == 'rollback'){
			assert new File(args[2]).exists(), "Changeset file doesn't exist"
			def filename = getChangeSetFilename(args[2])
			def tagOrCount = tagOrCount(args[3])

			println "Performing rollback from ${filename}, tag ${tagOrCount}"
			migrator.rollback filename, tagOrCount

		} else if (command == 'tag'){
			def tag = args[2]

			println "Performing setting a tag ${tag}"
			migrator.tag tag

		} else {
			throw new RuntimeException("Unknown command: ${args[1]}")
		}
	}

	private tagOrCount(tagOrCount) {
		try {
			tagOrCount.toInteger()
		} catch (Exception) {
			tagOrCount
		}
	}

	private parseDbConfig(String file){
		def properties = new Properties()
        properties.load new FileInputStream(file)
        def c = new ConfigSlurper().parse(properties)
		[driver: c.driver, url: c.url, username: c.username, password: c.password]
	}

	private getChangeSetFilename(String arg){
		if(arg.endsWith('.groovy')){
			def gen = new GroovyToChangeset()
			def file = gen.generateXmlChangeSetFile(new File(arg))
			file.name
		} else {
			arg
		}
	}
}

def s = new Starter()
s.run args.toList()

//def s = new Starter()
//s.run(['db.properties', 'migrate', 'changeset.groovy'])
//s.run(['db.properties', 'tag', 'mytag'])
//s.run(['db.properties', 'rollback',  'changeset.groovy', 1])