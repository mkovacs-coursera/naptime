package resources

import javax.inject.Inject
import javax.inject.Singleton

import org.coursera.example.Course
import org.coursera.naptime.Fields
import org.coursera.naptime.GetReverseRelation
import org.coursera.naptime.MultiGetReverseRelation
import org.coursera.naptime.Ok
import org.coursera.naptime.ResourceName
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.resources.CourierCollectionResource
import stores.CourseStore

@Singleton
class CoursesResource @Inject() (
    courseStore: CourseStore)
  extends CourierCollectionResource[String, Course] {

  override def resourceName = "courses"
  override def resourceVersion = 1
  override implicit lazy val Fields: Fields[Course] = BaseFields
    .withReverseRelations(
      "instructors" -> MultiGetReverseRelation(
        resourceName = ResourceName("instructors", 1),
        ids = "$instructorIds"),
      "partner" -> GetReverseRelation(
        resourceName = ResourceName("partners", 1),
        id = "$partnerId",
        description = "Partner who produces this course."),
      "courseMetadata/org.coursera.example.CourseMetadata/certificateInstructor" ->
        GetReverseRelation(
          resourceName = ResourceName("instructors", 1),
          id = "${courseMetadata/org.coursera.example.CourseMetadata/certificateInstructorId}",
          description = "Instructor who's name and signature appears on the course certificate."))

  def get(id: String = "v1-123") = Nap.get { context =>
    OkIfPresent(id, courseStore.get(id))
  }

  def multiGet(ids: Set[String], types: Set[String] = Set("course", "specialization")) = Nap.multiGet { context =>
    Ok(courseStore.all()
      .filter(course => ids.contains(course._1))
      .map { case (id, course) => Keyed(id, course) }.toList)
  }

  def getAll() = Nap.getAll { context =>

    val courses = courseStore.all().toList.map { case (id, course) => Keyed(id, course) }
    val coursesAfterNext = context.paging.start
      .map(s => courses.dropWhile(_.key != s))
      .getOrElse(courses)

    val coursesSubset = coursesAfterNext.take(context.paging.limit)

    val next = coursesAfterNext.drop(context.paging.limit).headOption.map(_.key)

    Ok(coursesSubset)
      .withPagination(next, Some(courses.size.toLong))
  }

  def byInstructor(instructorId: String) = Nap.finder { context =>
    val courses = courseStore.all()
      .filter(course => course._2.instructorIds.map(_.toString).contains(instructorId))
    Ok(courses.toList.map { case (id, course) => Keyed(id, course) })
  }

}
