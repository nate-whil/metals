package scala.meta.internal.metals

import java.nio.file.Files

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import scala.meta.internal.builds.BuildTools
import scala.meta.io.AbsolutePath

class BspConnector(
    bloopServers: BloopServers,
    bspServers: BspServers
) {

  def connect(
      workspace: AbsolutePath,
      userConfiguration: UserConfiguration,
      buildTools: BuildTools
  )(implicit ec: ExecutionContext): Future[Option[BspSession]] = {

    def connect(
        workspace: AbsolutePath
    ): Future[Option[BuildServerConnection]] = {
      if (buildTools.isBloop)
        bloopServers.newServer(workspace, userConfiguration)
      else
        bspServers.newServer(workspace)
    }

    val metaDirectories =
      if (buildTools.isSbt) sbtMetaWorkspaces(workspace) else List.empty

    connect(workspace).flatMap { mainOpt =>
      mainOpt match {
        case None => Future.successful(None)
        case Some(main) =>
          val metaConns = metaDirectories.map(connect(_))
          Future
            .sequence(metaConns)
            .map(meta => Some(BspSession(main, meta.flatten)))
      }
    }

  }

  private def sbtMetaWorkspaces(root: AbsolutePath): List[AbsolutePath] = {
    def recursive(
        p: AbsolutePath,
        acc: List[AbsolutePath]
    ): List[AbsolutePath] = {
      val projectDir = p.resolve("project")
      val bloopDir = projectDir.resolve(".bloop")
      if (Files.exists(bloopDir.toNIO))
        recursive(projectDir, projectDir :: acc)
      else
        acc
    }
    recursive(root, List.empty)
  }

}
