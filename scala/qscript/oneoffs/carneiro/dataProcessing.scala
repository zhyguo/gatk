import org.broadinstitute.sting.queue.extensions.gatk._
import org.broadinstitute.sting.queue.extensions.picard.PicardBamJarFunction
import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.queue.function.ListWriterFunction
import scala.io.Source


class dataProcessing extends QScript {
  qscript =>

  @Input(doc="path to GenomeAnalysisTK.jar", shortName="gatk", required=true)
  var GATKjar: File = _

  @Input(doc="path to AnalyzeCovariates.jar", shortName="ac", required=true)
  var ACJar: File = _

  @Input(doc="path to Picard's MarkDuplicates.jar", shortName="dedup", required=true)
  var dedupJar: File = _

  @Input(doc="path to R resources folder inside the Sting repository", shortName="r", required=true)
  var R: String = _

  @Input(doc="input BAM file - or list of BAM files", shortName="i", required=true)
  var input: String = _

  @Input(doc="Reference fasta file", shortName="R", required=false)
  var reference: File = new File("/seq/references/Homo_sapiens_assembly19/v1/Homo_sapiens_assembly19.fasta")

  @Input(doc="dbsnp ROD to use (VCF)", shortName="D", required=false)
  val dbSNP: File = new File("/humgen/gsa-hpprojects/GATK/data/dbsnp_132_b37.leftAligned.vcf")

  @Input(doc="extra VCF files to use as reference indels for Indel Realignment", shortName="indels", required=false)
  val indels: File = "/humgen/gsa-hpprojects/GATK/data/Comparisons/Unvalidated/AFR+EUR+ASN+1KG.dindel_august_release_merged_pilot1.20110126.sites.vcf"

  @Input(doc="the project name determines the final output (BAM file) base name. Example NA12878 yields NA12878.processed.bam", shortName="p", required=false)
  var projectName: String = "combined"

  @Input(doc="output path", shortName="outputDir", required=false)
  var outputDir: String = ""

  @Input(doc="the -L interval string to be used by GATK - output bams at interval only", shortName="L", required=false)
  var intervalString: String = ""

  @Input(doc="output bams at intervals only", shortName="intervals", required=false)
  var intervals: File = _

  val queueLogDir: String = ".qlog/"


  // Simple boolean definitions for code clarity
  val knownsOnly: Boolean = true
  val intermediate: Boolean = true

  // General arguments to all programs
  trait CommandLineGATKArgs extends CommandLineGATK {
    this.jarFile = qscript.GATKjar
    this.reference_sequence = qscript.reference
    this.memoryLimit = Some(4)
    this.isIntermediate = true
  }


  def script = {

    var perLaneBamList: List[String] = Nil
    var recalibratedBamList: List[File] = Nil


    // Helpful variables
    val outName: String        = qscript.projectName
    val outDir: String         = qscript.outputDir

    // BAM files generated by the pipeline
    val bamList: String         = outDir + outName + ".list"
    val cleanedBam: String      = outDir + outName + ".clean.bam"
    val fixedBam: String        = outDir + outName + ".processed.bam"

    // Accessory files
    val knownTargetIntervals: String = outDir + outName + ".known_indels.intervals"
    val allTargetIntervals: String    = outDir + outName + ".all_indels.intervals"

    add(new knownTargets(knownTargetIntervals))

    // Populates the list of per lane bam files to process (single bam or list of bams).
    if (input.endsWith("bam"))
      perLaneBamList :+= input
    else
      for (bam <- Source.fromFile(input).getLines())
        perLaneBamList :+= bam

    perLaneBamList.foreach { perLaneBam =>

      // Helpful variables
      val baseName: String        = swapExt(new File(perLaneBam.substring(perLaneBam.lastIndexOf("/")+1)), ".bam", "").toString()
      val baseDir: String         = perLaneBam.substring(0, perLaneBam.lastIndexOf("/")+1)

      // BAM files generated by the pipeline
      val cleanedBam: String      = baseName + ".clean.bam"
      val dedupedBam: String      = baseName + ".clean.dedup.bam"
      val recalBam: String        = baseName + ".clean.dedup.recal.bam"

      // Accessory files
      val metricsFile: String     = baseName + ".metrics"
      val preRecalFile: String    = baseName + ".pre_recal.csv"
      val postRecalFile: String   = baseName + ".post_recal.csv"
      val preOutPath: String      = baseName + ".pre"
      val postOutPath: String     = baseName + ".post"

      add(new clean(perLaneBam, knownTargetIntervals, cleanedBam, knownsOnly, intermediate),
          new dedup(cleanedBam, dedupedBam, metricsFile),
          new cov(dedupedBam, preRecalFile),
          new recal(dedupedBam, preRecalFile, recalBam),
          new cov(recalBam, postRecalFile),
          new analyzeCovariates(preRecalFile, preOutPath),
          new analyzeCovariates(postRecalFile, postOutPath))

      recalibratedBamList :+= new File(recalBam)
    }


    add(new writeList(recalibratedBamList, bamList),
        new allTargets(bamList, allTargetIntervals),
        new clean(bamList, allTargetIntervals, cleanedBam, !knownsOnly, !intermediate))
  }

  class TargetBase (outIntervals: String) extends RealignerTargetCreator with CommandLineGATKArgs {
      this.out = new File(outIntervals)
      this.mismatchFraction = Some(0.0)
      this.rodBind :+= RodBind("dbsnp", "VCF", dbSNP)
      this.rodBind :+= RodBind("indels", "VCF", indels)

  }

  class knownTargets (outIntervals: String) extends TargetBase(outIntervals) {
      this.jobName = queueLogDir + outIntervals + ".ktarget"
  }

  class allTargets (inBams: String, outIntervals: String) extends TargetBase(outIntervals) {
      this.input_file :+= new File(inBams)
      this.jobName = queueLogDir + outIntervals + ".atarget"
  }

  class clean (inBams: String, tIntervals: String, outBam: String, knownsOnly: Boolean, intermediate: Boolean) extends IndelRealigner with CommandLineGATKArgs {
    this.input_file :+= new File(inBams)
    this.targetIntervals = new File(tIntervals)
    this.out = new File(outBam)
    this.rodBind :+= RodBind("dbsnp", "VCF", dbSNP)
    this.rodBind :+= RodBind("indels", "VCF", indels)
    this.useOnlyKnownIndels = knownsOnly
    this.doNotUseSW = true
    this.baq = Some(org.broadinstitute.sting.utils.baq.BAQ.CalculationMode.CALCULATE_AS_NECESSARY)
    this.compress = Some(0)
    this.isIntermediate = intermediate
    this.jobName = queueLogDir + outBam + ".clean"
    if (!intermediate && !qscript.intervalString.isEmpty()) this.intervalsString ++= List(qscript.intervalString)
    if (!intermediate && qscript.intervals != null) this.intervals :+= qscript.intervals
  }

  class dedup (inBam: String, outBam: String, metricsFile: String) extends PicardBamJarFunction {
    @Input(doc="fixed bam") var clean: File = new File(inBam)
    @Output(doc="deduped bam") var deduped: File = new File(outBam)
    @Output(doc="deduped bam index") var dedupedIndex: File = new File(outBam + ".bai")
    @Output(doc="metrics file") var metrics: File = new File(metricsFile)
    override def inputBams = List(clean)
    override def outputBam = deduped
    override def commandLine = super.commandLine + " M=" + metricsFile + " CREATE_INDEX=true"
    sortOrder = null
    this.memoryLimit = Some(6)
    this.jarFile = qscript.dedupJar
    this.isIntermediate = true
    this.jobName = queueLogDir + outBam + ".dedup"
  }

  class cov (inBam: String, outRecalFile: String) extends CountCovariates with CommandLineGATKArgs {
    this.rodBind :+= RodBind("dbsnp", "VCF", dbSNP)
    this.covariate ++= List("ReadGroupCovariate", "QualityScoreCovariate", "CycleCovariate", "DinucCovariate")
    this.input_file :+= new File(inBam)
    this.recal_file = new File(outRecalFile)
    this.jobName = queueLogDir + outRecalFile + ".covariates"
  }

  class recal (inBam: String, inRecalFile: String, outBam: String) extends TableRecalibration with CommandLineGATKArgs {
    @Output(doc="recalibrated bam index") var recalIndex: File = new File(outBam + ".bai")
    this.input_file :+= new File (inBam)
    this.recal_file = new File(inRecalFile)
    this.out = new File(outBam)
    this.index_output_bam_on_the_fly = Some(true)
    this.jobName = queueLogDir + outBam + ".recalibration"
  }

  class analyzeCovariates (inRecalFile: String, outPath: String) extends AnalyzeCovariates {
    this.jarFile = qscript.ACJar
    this.resources = qscript.R
    this.recal_file = new File(inRecalFile)
    this.output_dir = outPath
    this.jobName = queueLogDir + inRecalFile + ".analyze_covariates"
  }

  class writeList(inBams: List[File], outBamList: String) extends ListWriterFunction {
    this.inputFiles = inBams
    this.listFile = new File(outBamList)
    this.jobName = queueLogDir + outBamList + ".bamList"
  }
}
